/*
 * Copyright 2020 Johnny850807 (Waterball) 潘冠辰
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *       http://www.apache.org/licenses/LICENSE-2.0
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package tw.waterball.judgegirl.commons.exceptions;

import static tw.waterball.judgegirl.commons.utils.StringUtils.capitalize;

/**
 * @author - johnny850807@gmail.com (Waterball)
 */
public class NotFoundException extends RuntimeException {
    public NotFoundException() {
    }

    public NotFoundException(Throwable cause) {
        super(cause);
    }

    public NotFoundException(String message) {
        super(message);
    }

    public NotFoundException(Object id, String resourceName) {
        this(id, "id", resourceName);
    }

    public NotFoundException(Object id, String identifierName, String resourceName) {
        super(String.format("Resource (%s) not found (%s = %s).",
                capitalize(resourceName), identifierName, id));
    }

    public static boolean isNotFound(Runnable runnable) {
        try {
            runnable.run();
            return false;
        } catch (NotFoundException err) {
            return true;
        }
    }

    public static NotFoundExceptionBuilder notFound(Class<?> resourceType) {
        return notFound(resourceType.getSimpleName());
    }

    public static NotFoundExceptionBuilder notFound(String resourceName) {
        return new NotFoundExceptionBuilder(resourceName);
    }

    public static class NotFoundExceptionBuilder {
        private final String resourceName;

        public NotFoundExceptionBuilder(String resourceName) {
            this.resourceName = resourceName;
        }

        public NotFoundException identifiedBy(String identifierName, Object id) {
            return new NotFoundException(identifierName, resourceName);
        }

        public NotFoundException id(Object id) {
            return new NotFoundException(id, resourceName);
        }

        public NotFoundException message(Object messageObj) {
            return message(messageObj.toString());
        }
        
        public NotFoundException message(String message) {
            return new NotFoundException(String.format("Resource (%s) not found: %s.",
                    resourceName, message));
        }
    }
}
