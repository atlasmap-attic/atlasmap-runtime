package io.atlasmap.java.module;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.LoggerFactory;

import io.atlasmap.api.AtlasException;
import io.atlasmap.core.DefaultAtlasConversionService;
import io.atlasmap.core.PathUtil;
import io.atlasmap.core.PathUtil.SegmentContext;
import io.atlasmap.java.inspect.ClassHelper;
import io.atlasmap.java.v2.JavaEnumField;
import io.atlasmap.java.v2.JavaField;
import io.atlasmap.v2.Field;
import io.atlasmap.v2.FieldType;

public class DocumentJavaFieldWriter {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(DocumentJavaFieldWriter.class);

    private Object rootObject = null;
    private Map<String, Class<?>> classesForFields = new HashMap<>();
    private JavaWriterUtil writerUtil = new JavaWriterUtil(DefaultAtlasConversionService.getInstance());
    private List<String> processedPaths = new LinkedList<>();

    public interface JavaFieldWriterValueConverter {
        Object convertValue(Object parentObject, Field targetField) throws AtlasException;
    }

    public DocumentJavaFieldWriter() {
    }

    public void write(Field field, JavaFieldWriterValueConverter converter) throws AtlasException {
        try {
            if (field == null) {
                throw new AtlasException(new IllegalArgumentException("Argument 'field' cannot be null"));
            }

            if (logger.isDebugEnabled()) {
                logger.debug("Now processing field: " + field);
                logger.debug("Field type: " + field.getFieldType());
                logger.debug("Field path: " + field.getPath());
                logger.debug("Field value: " + field.getValue());
                String fieldClassName = (field instanceof JavaField) ? ((JavaField) field).getClassName()
                        : ((JavaEnumField) field).getClassName();
                logger.debug("Field className: " + fieldClassName);
            }

            processedPaths.add(field.getPath());

            PathUtil path = new PathUtil(field.getPath());
            Object parentObject = rootObject;
            boolean segmentIsComplexSegment = true;
            for (SegmentContext segmentContext : path.getSegmentContexts(true)) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Now processing segment: " + segmentContext);
                    logger.debug("Parent object is currently: " + writeDocumentToString(false, parentObject));
                }

                if ("/".equals(segmentContext.getSegmentPath())) {
                    if (rootObject == null) {
                        if (logger.isDebugEnabled()) {
                            logger.debug("Creating root node: " + segmentContext);
                        }
                        rootObject = createParentObject(field, parentObject, segmentContext);
                    } else {
                        if (logger.isDebugEnabled()) {
                            logger.debug("Root node already exists, skipping segment: " + segmentContext);
                        }
                    }
                    parentObject = rootObject;
                    continue;
                }

                // if we're on the last segment, the
                boolean segmentIsLastSegment = (segmentContext.getNext() == null);
                if (segmentIsLastSegment) {
                    if (FieldType.COMPLEX.equals(field.getFieldType())) {
                        segmentIsComplexSegment = true;
                    } else {
                        segmentIsComplexSegment = false;
                    }
                    if (field instanceof JavaEnumField) {
                        segmentIsComplexSegment = false;
                    }
                }
                if (logger.isDebugEnabled()) {
                    if (segmentIsComplexSegment) {
                        logger.debug("Now processing complex segment: " + segmentContext);
                    } else if (field instanceof JavaEnumField) {
                        logger.debug("Now processing field enum value segment: " + segmentContext);
                    } else {
                        logger.debug("Now processing field value segment: " + segmentContext);
                    }
                }

                if (segmentIsComplexSegment) { // processing parent object
                    Object childObject = findChildObject(field, segmentContext, parentObject);

                    if (childObject == null) {
                        childObject = createParentObject(field, parentObject, segmentContext);
                    }
                    parentObject = childObject;
                } else { // processing field value
                    if (PathUtil.isCollectionSegment(segmentContext.getSegment())) {
                        parentObject = findOrCreateOrExpandParentCollectionObject(field, parentObject, segmentContext);
                    }
                    Object value = converter.convertValue(parentObject, field);
                    addChildObject(field, segmentContext, parentObject, value);
                }
            }
        } catch (Throwable t) {
            if (logger.isDebugEnabled()) {
                logger.debug("Error occured while writing field: " + field.getPath(), t);
            }
            if (t instanceof AtlasException) {
                throw (AtlasException) t;
            }
            throw new AtlasException(t);
        }
    }

    public Object findChildObject(Field field, SegmentContext segmentContext, Object parentObject)
            throws AtlasException {
        if (parentObject == null) {
            if (this.rootObject != null && segmentContext.getSegmentPath().equals("/")) {
                return this.rootObject;
            }
            return null;
        }

        String segment = segmentContext.getSegment();
        String parentSegment = segmentContext.getPrev() == null ? null : segmentContext.getPrev().getSegment();
        if (logger.isDebugEnabled()) {
            logger.debug("Looking for child object '" + segment + "' in parent '" + parentSegment + "': "
                    + writeDocumentToString(false, parentObject));
        }

        // find the child object on the given parent
        Object childObject = writerUtil.getObjectFromParent(field, parentObject, segmentContext);
        if (childObject != null && PathUtil.isCollectionSegment(segment)) {
            if (!collectionHasRoomForIndex(childObject, segmentContext)) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Found child collection '" + segment + "' (" + childObject.getClass().getName()
                            + ") in parent '" + parentSegment
                            + "', but it doesn't have room for the segment's index. Parent Object: "
                            + writeDocumentToString(false, parentObject));
                }
                return null;
            }
            childObject = getCollectionItem(childObject, segmentContext);
        }

        if (logger.isDebugEnabled()) {
            if (childObject == null) {
                logger.debug("Could not find child object '" + segment + "' in parent '" + parentSegment + "'.");
            } else {
                logger.debug("Found child object '" + segment + "' in parent '" + parentSegment + "', class: "
                        + childObject.getClass().getName() + ", child object: "
                        + writeDocumentToString(false, childObject));
            }
        }

        return childObject;
    }

    public Object createParentObject(Field field, Object parentObject, SegmentContext segmentContext)
            throws AtlasException {
        String segment = segmentContext.getSegment();
        if (logger.isDebugEnabled()) {
            logger.debug("Creating parent object: " + segmentContext);
        }
        Object childObject = null;
        if (PathUtil.isCollectionSegment(segment)) {
            // first, let's see if we have the collection object at all
            if (logger.isDebugEnabled()) {
                logger.debug(
                        "Looking for collection wrapper child for " + segmentContext + " on parent: " + parentObject);
            }
            Object collectionObject = findOrCreateOrExpandParentCollectionObject(field, parentObject, segmentContext);
            childObject = getCollectionItem(collectionObject, segmentContext);

            if (childObject == null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Could not find child object in collection, creating it.");
                }
                childObject = createObject(field, segmentContext, parentObject, false);
                addChildObject(field, segmentContext, collectionObject, childObject);
            }

            if (logger.isDebugEnabled()) {
                logger.debug("Child object inside collection wrapper for segment '" + segment + "': "
                        + writeDocumentToString(false, childObject));
            }
        } else {
            childObject = createObject(field, segmentContext, parentObject, false);
            addChildObject(field, segmentContext, parentObject, childObject);
        }
        if (logger.isDebugEnabled()) {
            logger.debug(
                    "Created child object for segment '" + segment + "': " + writeDocumentToString(true, childObject));
        }
        return childObject;
    }

    public Object findOrCreateOrExpandParentCollectionObject(Field field, Object parentObject,
            SegmentContext segmentContext) throws AtlasException {
        String segment = segmentContext.getSegment();
        // first, let's see if we have the collection object at all
        if (logger.isDebugEnabled()) {
            logger.debug("Looking for collection wrapper child for " + segmentContext + " on parent: " + parentObject);
        }
        Object collectionObject = writerUtil.getObjectFromParent(field, parentObject, segmentContext);
        if (collectionObject == null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Cannot find pre-existing child collection for segment '" + segment
                        + "', creating the collection.");
            }
            collectionObject = createCollectionWrapperObject(field, segmentContext, parentObject);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Collection wrapper child object for segment '" + segment + "': "
                    + writeDocumentToString(false, collectionObject));
        }

        collectionObject = expandCollectionToFitItem(field, collectionObject, segmentContext, parentObject);
        addChildObject(field, segmentContext, parentObject, collectionObject);

        return collectionObject;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Object expandCollectionToFitItem(Field field, Object collectionObject, SegmentContext segmentContext,
            Object parentObject) throws AtlasException {
        String segment = segmentContext.getSegment();
        if (!collectionHasRoomForIndex(collectionObject, segmentContext)) {
            if (logger.isDebugEnabled()) {
                logger.debug("Collection is not large enough for segment '" + segment + "', expanding the collection.");
            }
            int index = PathUtil.indexOfSegment(segment);
            if (collectionObject instanceof List) {
                List list = (List) collectionObject;
                while (list.size() < (index + 1)) {
                    list.add(null);
                }
            } else if (collectionObject instanceof Map) {
                throw new AtlasException("FIXME: Cannot yet handle adding children to maps");
            } else if (collectionObject.getClass().isArray()) {
                if (Array.getLength(collectionObject) < (index + 1)) {
                    // resize the array to fit the item
                    Object newArray = (Object) createObject(field, segmentContext, parentObject, true);
                    // copy pre-existing items over to new array
                    for (int i = 0; i < Array.getLength(collectionObject); i++) {
                        Array.set(newArray, i, Array.get(collectionObject, i));
                    }
                    collectionObject = newArray;
                }
            }

            if (logger.isDebugEnabled()) {
                logger.debug("Finished expanding collection: " + collectionObject);
            }
        }
        return collectionObject;
    }

    public Object createCollectionWrapperObject(Field field, SegmentContext segmentContext, Object parentObject)
            throws AtlasException {
        // create the "List" part of List<Contact>
        String segment = segmentContext.getSegment();
        if (PathUtil.isArraySegment(segment)) {
            return createObject(field, segmentContext, parentObject, true);
        } else if (PathUtil.isListSegment(segment)) {
            // TODO: look up field level or document level default list impl
            return writerUtil.instantiateObject(LinkedList.class, segmentContext, false);
        } else if (PathUtil.isMapSegment(segment)) {
            // TODO: look up field level or document level default map impl
            return writerUtil.instantiateObject(HashMap.class, segmentContext, false);
        }
        throw new AtlasException("Can't create collection object for segment: " + segment);
    }

    public Class<?> getClassForField(Field field, SegmentContext segmentContext, Object parentObject,
            boolean unwrapCollectionType) throws AtlasException {
        if (logger.isDebugEnabled()) {
            logger.debug(
                    "Looking up class to use for segment: " + segmentContext + "\n\tparentObject: " + parentObject);
        }
        Class<?> clz = null;

        if (logger.isDebugEnabled()) {
            logger.debug("Looking for configured class for field: " + field + ".");
        }
        String className = null;
        if (field instanceof JavaField) {
            className = ((JavaField) field).getClassName();
        } else if (field instanceof JavaEnumField) {
            className = ((JavaEnumField) field).getClassName();
        }
        if (className != null) {
            try {
                clz = className == null ? null : Class.forName(className);
            } catch (Exception e) {
                throw new AtlasException("Could not find class for '" + className + "', for segment: " + segmentContext
                        + ", on field: " + field, e);
            }
        }

        if (clz == null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Couldn't find class on field. Looking for configured class for segment: " + segmentContext
                        + ".");
            }
            String normalizedSegment = PathUtil.removeCollectionIndexes(segmentContext.getSegmentPath());
            clz = this.classesForFields.get(normalizedSegment);
        }
        if (clz == null) { // attempt to determine it from the parent object.
            if (logger.isDebugEnabled()) {
                logger.debug("Couldn't find configured class for segment: " + segmentContext
                        + ", looking up getter method.");
            }
            Method m = null;
            try {
                String methodName = "get"
                        + JavaWriterUtil.capitalizeFirstLetter(PathUtil.cleanPathSegment(segmentContext.getSegment()));
                m = ClassHelper.detectGetterMethod(parentObject.getClass(), methodName);
            } catch (NoSuchMethodException e) {
                // it's ok, we didnt find a getter.
                if (logger.isDebugEnabled()) {
                    logger.debug("Couldn't find getter method for segment: " + segmentContext, e);
                }
            }
            clz = m == null ? null : m.getReturnType();
        }
        if (clz == null) {
            throw new AtlasException(
                    "Could not create object, can't find class to instantiate for segment: " + segmentContext);
        }
        if (unwrapCollectionType && clz.isArray()) {
            Class<?> oldClass = clz;
            clz = clz.getComponentType();
            if (logger.isDebugEnabled()) {
                logger.debug(
                        "Unwrapped type '" + clz.getName() + "' from wrapper array type '" + oldClass.getName() + "'.");
            }
        } else if (unwrapCollectionType && Collection.class.isAssignableFrom(clz)) {
            Class<?> oldClass = clz;
            clz = null;
            String cleanedSegment = PathUtil.cleanPathSegment(segmentContext.getSegment());
            for (java.lang.reflect.Field declaredField : parentObject.getClass().getDeclaredFields()) {
                if (cleanedSegment.equals(declaredField.getName())) {
                    if (declaredField.getGenericType() == null) {
                        if (logger.isDebugEnabled()) {
                            logger.debug("Skipping determining generic type declared field '" + declaredField.getName()
                                    + "', the field isn't generic. Segment: " + segmentContext);
                        }
                        continue;
                    }
                    ParameterizedType paramType = (ParameterizedType) declaredField.getGenericType();
                    String typeName = paramType.getActualTypeArguments()[0].getTypeName();
                    try {
                        clz = typeName == null ? null : Class.forName(typeName);
                    } catch (Exception e) {
                        throw new AtlasException("Could not find class for '" + typeName + "', for segment: "
                                + segmentContext + ", on field: " + field, e);
                    }
                }
            }
            if (clz == null) {
                throw new AtlasException(
                        "Could not unwrap list collection's generic type for segment: " + segmentContext);
            }
            if (logger.isDebugEnabled()) {
                logger.debug(
                        "Unwrapped type '" + clz.getName() + "' from wrapper list type '" + oldClass.getName() + "'.");
            }
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Found class '" + clz.getName() + "' to use for segment: " + segmentContext);
        }
        return clz;
    }

    public Object createObject(Field javaField, SegmentContext segmentContext, Object parentObject,
            boolean createWrapperArray) throws AtlasException {
        return writerUtil.instantiateObject(getClassForField(javaField, segmentContext, parentObject, true),
                segmentContext, createWrapperArray);
    }

    public Object getCollectionItem(Object collection, SegmentContext segmentContext) throws AtlasException {
        String segment = segmentContext.getSegment();
        int index = PathUtil.indexOfSegment(segment);
        if (PathUtil.isArraySegment(segment)) {
            return Array.get(collection, index);
        } else if (PathUtil.isListSegment(segment)) {
            return ((List<?>) collection).get(index);
        } else if (PathUtil.isMapSegment(segment)) {
            throw new AtlasException("Maps are currently unhandled for segment: " + segment);
        }
        throw new AtlasException("Cannot determine collection type from segment: " + segment);
    }

    public boolean collectionHasRoomForIndex(Object collection, SegmentContext segmentContext) throws AtlasException {
        String segment = segmentContext.getSegment();
        int index = PathUtil.indexOfSegment(segment);
        int size = getCollectionSize(collection);
        boolean result = size > index;
        if (logger.isDebugEnabled()) {
            logger.debug("collectionHasRoomForIndex: " + result + ", size: " + size + ", index: " + index);
        }
        return result;
    }

    public int getCollectionSize(Object collection) throws AtlasException {
        if (collection instanceof List) {
            return ((List<?>) collection).size();
        } else if (collection instanceof Map) {
            return ((Map<?, ?>) collection).size();
        } else if (collection.getClass().isArray()) {
            return Array.getLength(collection);
        }
        throw new AtlasException("Cannot determine collection size for: " + collection);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addChildObject(Field field, SegmentContext segmentContext, Object parentObject, Object childObject)
            throws AtlasException {
        if (logger.isDebugEnabled()) {
            logger.debug("Adding child object for segment: " + segmentContext + "\n\tparentObject: " + parentObject
                    + "\n\tchild: " + childObject);
        }
        if (this.rootObject == null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Setting root object: " + childObject);
            }
            this.rootObject = childObject;
            return;
        }
        boolean parentIsCollection = (parentObject instanceof Collection) || (parentObject.getClass().isArray());
        if (parentIsCollection) {
            String segment = segmentContext.getSegment();
            int index = PathUtil.indexOfSegment(segment);
            if (parentObject instanceof List) {
                List list = (List) parentObject;
                if (index >= list.size()) {
                    throw new AtlasException("Cannot fit item in list, list size: " + list.size() + ", item index: "
                            + index + ", segment: " + segmentContext);
                }
                list.set(index, childObject);
            } else if (parentObject instanceof Map) {
                throw new AtlasException("FIXME: Cannot yet handle adding children to maps");
            } else if (parentObject.getClass().isArray()) {
                if (index >= Array.getLength(parentObject)) {
                    throw new AtlasException("Cannot fit item in array, array size: " + Array.getLength(parentObject)
                            + ", item index: " + index + ", segment: " + segmentContext);
                }
                try {
                    Array.set(parentObject, index, childObject);
                } catch (Exception e) {
                    String parentClass = parentObject == null ? null : parentObject.getClass().getName();
                    String childClass = childObject == null ? null : childObject.getClass().getName();
                    throw new AtlasException("Could not set child class '" + childClass + "' on parent '" + parentClass
                            + "' for: " + segmentContext, e);
                }
            } else {
                throw new AtlasException("Cannot determine collection type for: " + parentObject);
            }
        } else {
            writerUtil.setObjectOnParent(field, segmentContext, parentObject, childObject);
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Finished adding child object for segment: " + segmentContext + "\n\tparentObject: "
                    + parentObject + "\n\t: " + childObject);
        }
    }

    public static String writeDocumentToString(boolean stripSpaces, Object object) throws AtlasException {
        try {
            if (object == null) {
                return "";
            }

            String result = object.toString();

            if (stripSpaces) {
                result = result.replaceAll("\n|\r", "");
                result = result.replaceAll("> *?<", "><");
            }
            return result;
        } catch (Exception e) {
            throw new AtlasException(e);
        }
    }

    public Object getRootObject() {
        return rootObject;
    }

    public void addClassForFieldPath(String fieldPath, Class<?> clz) {
        fieldPath = PathUtil.removeCollectionIndexes(fieldPath);
        this.classesForFields.put(fieldPath, clz);
    }

    public void clearClassesForFields() {
        this.classesForFields.clear();
    }

    public void setWriterUtil(JavaWriterUtil writerUtil) {
        this.writerUtil = writerUtil;
    }

    public void setRootObject(Object rootObject) {
        this.rootObject = rootObject;
    }

    public List<String> getProcessedPaths() {
        return processedPaths;
    }
}
