package io.atlasmap.reference.jsonToJson;

import io.atlasmap.json.v2.JsonField;
import io.atlasmap.reference.AtlasBaseActionsTest;
import io.atlasmap.v2.Field;
import io.atlasmap.v2.FieldType;

public class JsonJsonFieldActionsTest extends AtlasBaseActionsTest {

    public JsonJsonFieldActionsTest() {
        this.sourceField = createField("/contact/firstName");
        this.targetField = createField("/contact/firstName");
        this.docURI = "atlas:json";
    }

    protected Field createField(String path) {
        JsonField f = new JsonField();
        f.setPath(path);
        f.setFieldType(FieldType.STRING);
        return f;
    }

    @Override
    public Object createInput(String inputFirstName) {
        return "{ \"contact\": { \"firstName\": \"" + inputFirstName + "\" } }";
    }

    public Object getOutputValue(Object output) {
        System.out.println("Extracting output value from: " + output);
        String result = (String) output;
        result = result.substring("{\"contact\":{\"firstName\":\"".length());
        result = result.substring(0, result.length() - "\"}}".length());
        System.out.println("Output value extracted: " + result);
        return result;
    }

}
