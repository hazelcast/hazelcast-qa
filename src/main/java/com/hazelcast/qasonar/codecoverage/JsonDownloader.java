package com.hazelcast.qasonar.codecoverage;

import com.google.gson.JsonArray;
import com.hazelcast.qasonar.utils.PropertyReader;

import java.io.IOException;

import static com.hazelcast.qasonar.utils.Utils.getJsonElementsFromQuery;

public class JsonDownloader {

    private final PropertyReader props;

    public JsonDownloader(PropertyReader props) {
        this.props = props;
    }

    public JsonArray getJsonArrayFromQuery(String query) throws IOException {
        return getJsonElementsFromQuery(props.getUsername(), props.getPassword(), query);
    }
}
