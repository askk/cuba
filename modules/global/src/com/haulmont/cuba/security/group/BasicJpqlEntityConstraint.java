/*
 * Copyright (c) 2008-2019 Haulmont.
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

package com.haulmont.cuba.security.group;

public class BasicJpqlEntityConstraint extends BasicEntityConstraint implements JpqlEntityConstraint {
    protected String where;
    protected String join;

    @Override
    public String getWhere() {
        return where;
    }

    @Override
    public String getJoin() {
        return join;
    }

    public void setWhere(String where) {
        this.where = where;
    }

    public void setJoin(String join) {
        this.join = join;
    }
}
