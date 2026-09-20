package com.nutriverse.backend.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nutriverse.backend.dto.NutritionResult;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class NutritionProviderTests {
    private static final String SEARCH_FOOD = """
            {"description":"Bananas, raw","fdcId":173944,"dataType":"SR Legacy","foodNutrients":[
              {"nutrientNumber":"208","unitName":"KCAL","value":89},
              {"nutrientNumber":"268","unitName":"kJ","value":372},
              {"nutrientNumber":"203","unitName":"G","value":1.09},
              {"nutrientNumber":"204","unitName":"G","value":0.33},
              {"nutrientNumber":"205","unitName":"G","value":22.84},
              {"nutrientNumber":"291","unitName":"G","value":2.6},
              {"nutrientNumber":"301","unitName":"MG","value":5},
              {"nutrientNumber":"303","unitName":"MG","value":0.26},
              {"nutrientNumber":"306","unitName":"MG","value":358},
              {"nutrientNumber":"307","unitName":"MG","value":1}]}
            """;

    @Test
    void searchEncodesQueryDeduplicatesAndKeepsGovernmentProvenance() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://usda.example/fdc/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(request -> assertTrue(request.getURI().getRawQuery()
                        .contains("query=banana%20%26%20oats%20%7Bfresh%7D")))
                .andRespond(withSuccess("{\"foods\":[" + SEARCH_FOOD + "," + SEARCH_FOOD + "]}", MediaType.APPLICATION_JSON));
        List<NutritionResult> foods = new UsdaFoodDataProvider(builder.build(), "test-key")
                .search("banana & oats {fresh}");
        assertEquals(1, foods.size());
        NutritionResult food = foods.getFirst();
        assertEquals("173944", food.getSourceId());
        assertEquals("USDA FoodData Central", food.getSource());
        assertEquals("AUTHORITATIVE_DATABASE", food.getSourceType());
        assertEquals("SR Legacy", food.getDataType());
        assertTrue(food.isVerified());
        assertFalse(food.isEstimated());
        assertEquals(100.0, food.getServingSize());
        assertEquals("g", food.getServingUnit());
        assertEquals(89.0, food.getCalories());
        assertEquals(1.09, food.getProtein());
        assertEquals(0.33, food.getFat());
        assertEquals(22.84, food.getCarbs());
        assertEquals(2.6, food.getFiber());
        assertEquals(5.0, food.getCalcium());
        assertEquals(0.26, food.getIron());
        assertEquals(358.0, food.getPotassium());
        assertEquals(1.0, food.getSodium());
        server.verify();
    }

    @Test
    void exactFoundationDetailsPreferSpecificEnergyAndIgnoreInvalidOrWrongUnits() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://usda.example/fdc/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://usda.example/fdc/v1/food/100?api_key=test-key"))
                .andRespond(withSuccess("""
                        {"description":"Foundation food","fdcId":100,"dataType":"Foundation","foodNutrients":[
                          {"nutrient":{"number":"208","unitName":"kcal"},"amount":100},
                          {"nutrient":{"number":"957","unitName":"kcal"},"amount":99},
                          {"nutrient":{"number":"958","unitName":"kcal"},"amount":98},
                          {"nutrient":{"number":"203","unitName":"g"},"amount":"NaN"},
                          {"nutrient":{"number":"203","unitName":"g"},"amount":2},
                          {"nutrient":{"number":"204","unitName":"g"},"amount":-1},
                          {"nutrient":{"number":"301","unitName":"g"},"amount":0.2},
                          {"nutrient":{"number":"307","unitName":"mg"},"amount":"Infinity"}]}
                        """, MediaType.APPLICATION_JSON));
        NutritionResult food = new UsdaFoodDataProvider(builder.build(), "test-key").findBySourceId("100");
        assertNotNull(food);
        assertEquals(98.0, food.getCalories());
        assertEquals(2.0, food.getProtein());
        assertNull(food.getFat());
        assertNull(food.getCalcium());
        assertNull(food.getSodium());
        assertNull(food.getFiber());
        server.verify();
    }

    @Test
    void usdaRejectsMissingIdsAndBrandedFoods() {

        RestClient.Builder builder =
                RestClient.builder()
                        .baseUrl("https://usda.example");

        MockRestServiceServer server =
                MockRestServiceServer
                        .bindTo(builder)
                        .build();


        String noId =
                SEARCH_FOOD.replace(
                        "\"fdcId\":173944",
                        "\"fdcId\":0"
                );


        /*
         * Even if a USDA Branded record uses grams,
         * NutriVerse does not label manufacturer
         * branded data as government-verified food data.
         */
        String branded =
                SEARCH_FOOD.replace(
                        "\"SR Legacy\"",
                        "\"Branded\",\"servingSizeUnit\":\"g\""
                );


        server.expect(anything())
                .andRespond(
                        withSuccess(
                                "{\"foods\":["
                                        + noId
                                        + ","
                                        + branded
                                        + "]}",
                                MediaType.APPLICATION_JSON
                        )
                );


        List<NutritionResult> foods =
                new UsdaFoodDataProvider(
                        builder.build(),
                        "test-key"
                )
                        .search("banana");


        assertTrue(foods.isEmpty());

        server.verify();
    }

    @Test
    void usdaFailureLogsStatusWithoutCredentialResponseOrUserQuery() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://usda.example");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withBadRequest().body("test-private-key private-food-query"));
        Logger logger = (Logger) LoggerFactory.getLogger(UsdaFoodDataProvider.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                    () -> new UsdaFoodDataProvider(builder.build(), "test-private-key").search("private-food-query"));
            assertEquals(503, error.getStatusCode().value());
            String logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
            assertTrue(logs.contains("status=400"));
            assertFalse(logs.contains("test-private-key"));
            assertFalse(logs.contains("private-food-query"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
        server.verify();
    }

    @Test
    void exactLookupRejectsDifferentProviderFoodAndTreats404AsNotFound() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://usda.example");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withSuccess(SEARCH_FOOD, MediaType.APPLICATION_JSON));
        server.expect(anything()).andRespond(withResourceNotFound());
        UsdaFoodDataProvider provider = new UsdaFoodDataProvider(builder.build(), "test-key");
        assertThrows(ResponseStatusException.class, () -> provider.findBySourceId("200"));
        assertNull(provider.findBySourceId("300"));
        server.verify();
    }

    @Test
    void offKeepsMassProductsOnlyAndConvertsMineralsWithoutClaimingVerification() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://off.example");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String mass = """
                {"code":"1234567890123","product_name":"Oats","quantity":"500 g","serving_size":"1 bowl (30 g)","nutriments":{
                 "energy-kcal_100g":370,"proteins_100g":13,"sodium_100g":0.001,"iron_100g":0.0042}}
                """;
        String liquid = mass.replace("500 g", "500 ml").replace("1234567890123", "456");
        String unknown = mass.replace("\"quantity\":\"500 g\",", "")
                .replace("\"serving_size\":\"1 bowl (30 g)\",", "").replace("1234567890123", "789");
        String noId = mass.replace("1234567890123", "");
        server.expect(anything()).andRespond(withSuccess("{\"hits\":[" + mass + "," + mass + "," + liquid + "," + unknown + "," + noId + "]}", MediaType.APPLICATION_JSON));
        RestClient client = builder.build();
        List<NutritionResult> foods = new OpenFoodFactsProvider(client, client).search("oats");
        assertEquals(1, foods.size());
        NutritionResult food = foods.getFirst();
        assertEquals("Open Food Facts", food.getSource());
        assertEquals("PRODUCT_DATABASE", food.getSourceType());
        assertFalse(food.isVerified());
        assertFalse(food.isEstimated());
        assertEquals(1.0, food.getSodium());
        assertEquals(4.2, food.getIron(), 0.00001);
        assertNull(food.getFiber());
        server.verify();
    }

    @Test
    void lookupFallsBackAfterUsdaFailureButDoesNotConcealTotalOutage() {
        UsdaFoodDataProvider usda = mock(UsdaFoodDataProvider.class);
        OpenFoodFactsProvider off = mock(OpenFoodFactsProvider.class);
        NutritionLookupService lookup = new NutritionLookupService(usda, off);
        when(usda.search("banana")).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));
        NutritionResult product = new NutritionResult();
        when(off.search("banana")).thenReturn(List.of(product));
        assertEquals(List.of(product), lookup.search("banana"));
        when(off.search("banana")).thenReturn(List.of());
        assertThrows(ResponseStatusException.class, () -> lookup.search("banana"));
        doReturn(List.of()).when(usda).search("banana");
        assertEquals(List.of(), lookup.search("banana"));
    }

    @Test
    void exactSourceLookupNeverUsesTheOtherProviderAsFallback() {
        UsdaFoodDataProvider usda = mock(UsdaFoodDataProvider.class);
        OpenFoodFactsProvider off = mock(OpenFoodFactsProvider.class);
        when(usda.getProviderName()).thenReturn("USDA FoodData Central");
        when(usda.findBySourceId("173944")).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));
        NutritionLookupService lookup = new NutritionLookupService(usda, off);
        assertThrows(ResponseStatusException.class, () -> lookup.findBySourceId("USDA", "173944"));
        verifyNoInteractions(off);
    }
}
