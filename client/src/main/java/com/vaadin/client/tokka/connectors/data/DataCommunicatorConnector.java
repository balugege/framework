/*
 * Copyright 2000-2014 Vaadin Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vaadin.client.tokka.connectors.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.vaadin.client.ServerConnector;
import com.vaadin.client.data.AbstractRemoteDataSource;
import com.vaadin.client.data.DataSource;
import com.vaadin.client.extensions.AbstractExtensionConnector;
import com.vaadin.shared.data.DataRequestRpc;
import com.vaadin.shared.data.typed.DataCommunicatorClientRpc;
import com.vaadin.shared.data.typed.DataProviderConstants;
import com.vaadin.shared.ui.Connect;
import com.vaadin.tokka.server.communication.data.DataCommunicator;

import elemental.json.Json;
import elemental.json.JsonArray;
import elemental.json.JsonObject;

/**
 * A connector for DataCommunicator class.
 * 
 * @since
 */
@Connect(DataCommunicator.class)
public class DataCommunicatorConnector extends AbstractExtensionConnector {

    public class VaadinDataSource extends AbstractRemoteDataSource<JsonObject> {

        private Set<String> droppedKeys = new HashSet<String>();

        protected VaadinDataSource() {
            registerRpc(DataCommunicatorClientRpc.class,
                    new DataCommunicatorClientRpc() {

                        @Override
                        public void reset(int size) {
                            resetDataAndSize(size);
                        }

                        @Override
                        public void setData(int firstIndex, JsonArray data) {
                            ArrayList<JsonObject> rows = new ArrayList<JsonObject>(
                                    data.length());
                            for (int i = 0; i < data.length(); i++) {
                                JsonObject rowObject = data.getObject(i);
                                rows.add(rowObject);
                            }

                            setRowData(firstIndex, rows);
                        }

                        @Override
                        public void add(int index) {
                            VaadinDataSource.this.insertRowData(index, 1);
                        }

                        @Override
                        public void drop(int index) {
                            VaadinDataSource.this.removeRowData(index, 1);
                        }

                        @Override
                        public void updateData(JsonArray data) {
                            for (int i = 0; i < data.length(); ++i) {
                                updateRowData(data.getObject(i));
                            }
                        }
                    });
        }

        public RowHandle<JsonObject> getHandleByKey(String key) {
            JsonObject row = Json.createObject();
            row.put(DataProviderConstants.KEY, key);
            return new RowHandleImpl(row, key);
        }

        @Override
        protected void requestRows(int firstRowIndex, int numberOfRows,
                RequestRowsCallback<JsonObject> callback) {
            getRpcProxy(DataRequestRpc.class).requestRows(firstRowIndex,
                    numberOfRows, 0, 0);

            JsonArray dropped = Json.createArray();
            int i = 0;
            for (String key : droppedKeys) {
                dropped.set(i++, key);
            }

            getRpcProxy(DataRequestRpc.class).dropRows(dropped);
        }

        @Override
        public String getRowKey(JsonObject row) {
            return row.getString(DataProviderConstants.KEY);
        }

        @Override
        protected void onDropFromCache(int rowIndex, JsonObject removed) {
            droppedKeys.add(getRowKey(removed));

            super.onDropFromCache(rowIndex, removed);
        }

        /**
         * Updates row data based on row key.
         * 
         * @param row
         *            new row object
         */
        protected void updateRowData(JsonObject row) {
            int index = indexOfKey(getRowKey(row));
            if (index >= 0) {
                setRowData(index, Collections.singletonList(row));
            }
        }
    }

    private DataSource<JsonObject> ds = new VaadinDataSource();

    @Override
    protected void extend(ServerConnector target) {
        ServerConnector parent = getParent();
        if (parent instanceof HasDataSource) {
            ((HasDataSource) parent).setDataSource(ds);
        } else {
            assert false : "Parent not implementing HasDataSource";
        }
    }
}