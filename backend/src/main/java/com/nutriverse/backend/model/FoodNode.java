package com.nutriverse.backend.model;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("Food")
public class FoodNode {

    @Id
    private String name;

    public FoodNode() {}

    public FoodNode(String name) {this.name = name;}

    public String getName() {return name;}
    public void setName(String name) {
        this.name = name;
    }
}