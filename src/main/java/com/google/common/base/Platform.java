/*
 * Copyright (C) 2009 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.common.base;

/**
 * Methods factored out so that they can be emulated differently in GWT.
 *
 * @author Jesse Wilson
 */
final class Platform {

    private Platform() {
    }

    static boolean stringIsNullOrEmpty(String string) {
        return string == null || string.isEmpty();
    }

    /**
     * Returns the string if it is not null, or an empty string otherwise.
     *
     * @param string the string to test and possibly return
     * @return {@code string} if it is not null; {@code ""} otherwise
     */
    static String nullToEmpty(String string) {
        return (string == null) ? "" : string;
    }

    /**
     * Returns the string if it is not empty, or a null string otherwise.
     *
     * @param string the string to test and possibly return
     * @return {@code string} if it is not empty; {@code null} otherwise
     */
    static String emptyToNull(String string) {
        return stringIsNullOrEmpty(string) ? null : string;
    }
}
