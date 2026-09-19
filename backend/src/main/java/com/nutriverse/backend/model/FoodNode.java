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
    // Legacy graph nodes have no imported nutrition provenance.
    @org.springframework.data.annotation.Transient
    public String getSourceStatus() { return "Source not verified."; }

    @org.springframework.data.annotation.Transient
    public boolean isVerified() { return false; }
    public void setName(String name) {
        this.name = name;
    }
}
