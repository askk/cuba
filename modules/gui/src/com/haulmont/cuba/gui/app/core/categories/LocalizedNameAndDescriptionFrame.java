/*
 * Copyright (c) 2008-2017 Haulmont.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.haulmont.cuba.gui.app.core.categories;

import com.haulmont.chile.core.model.MetaClass;
import com.haulmont.cuba.core.entity.AttributeLocaleData;
import com.haulmont.cuba.gui.components.*;

public class LocalizedNameAndDescriptionFrame extends AbstractLocalizedTextFieldsFrame {

    @Override
    protected void createColumns(DataGrid<AttributeLocaleData> dataGrid) {
        MetaClass metaClass = metadata.getClass(AttributeLocaleData.class);

        dataGrid.addColumn(LANGUAGE_WITH_CODE, metadataTools.resolveMetaPropertyPath(metaClass, LANGUAGE_WITH_CODE))
                .setCaption(messageTools.getPropertyCaption(metaClass, LANGUAGE_WITH_CODE));
        dataGrid.addColumn(NAME, metadataTools.resolveMetaPropertyPath(metaClass, NAME))
                .setCaption(messageTools.getPropertyCaption(metaClass, NAME));
        dataGrid.addColumn(DESCRIPTION, metadataTools.resolveMetaPropertyPath(metaClass, DESCRIPTION))
                .setCaption(messageTools.getPropertyCaption(metaClass, DESCRIPTION));
    }

    @Override
    protected void configureColumns(DataGrid<AttributeLocaleData> dataGrid) {
        DataGrid.Column<AttributeLocaleData> langColumn = dataGrid.getColumnNN(LANGUAGE_WITH_CODE);
        DataGrid.Column<AttributeLocaleData> nameColumn = dataGrid.getColumnNN(NAME);
        DataGrid.Column<AttributeLocaleData> descriptionColumn = dataGrid.getColumnNN(DESCRIPTION);

        setColumnDescriptionProvider(langColumn, null);
        setColumnDescriptionProvider(nameColumn, AttributeLocaleData::getName);
        setColumnDescriptionProvider(descriptionColumn, AttributeLocaleData::getDescription);

        langColumn.setResizable(false);
        nameColumn.setResizable(false);
        descriptionColumn.setResizable(false);

        langColumn.setExpandRatio(1);
        nameColumn.setExpandRatio(3);
        descriptionColumn.setExpandRatio(4);

        langColumn.setEditable(false);
    }


    protected void setDescriptionProviders(DataGrid.Column<AttributeLocaleData> langColumn,
                                           DataGrid.Column<AttributeLocaleData> nameColumn,
                                           DataGrid.Column<AttributeLocaleData> descrColumn) {

        langColumn.setDescriptionProvider(attributeLocaleData -> getMessage("localeDataDescription"));

        nameColumn.setDescriptionProvider(attributeLocaleData -> {
            String nameValue = attributeLocaleData.getName() != null ? attributeLocaleData.getName() + "\n\n" : "";
            return nameValue + getMessage("localeDataDescription");
        });

        descrColumn.setDescriptionProvider(attributeLocaleData -> {
            String nameValue = attributeLocaleData.getDescription() != null ? attributeLocaleData.getDescription() + "\n\n" : "";
            return nameValue + getMessage("localeDataDescription");
        });
    }

    protected String getNamesValue() {
        return storeLocaleValues(AttributeLocaleData::getName);
    }

    protected String getDescriptionsValue() {
        return storeLocaleValues(AttributeLocaleData::getDescription);
    }

    protected void setNamesValue(String localeBundle) {
        loadLocaleValues(localeBundle, AttributeLocaleData::setName);
    }

    protected void setDescriptionsValue(String localeBundle) {
        loadLocaleValues(localeBundle, AttributeLocaleData::setDescription);
    }
}
