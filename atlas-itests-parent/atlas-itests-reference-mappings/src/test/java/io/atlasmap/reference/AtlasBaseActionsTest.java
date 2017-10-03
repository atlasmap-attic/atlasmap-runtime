package io.atlasmap.reference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.Test;

import io.atlasmap.api.AtlasContext;
import io.atlasmap.api.AtlasSession;
import io.atlasmap.core.AtlasMappingService.AtlasMappingFormat;
import io.atlasmap.core.DefaultAtlasContextFactory;
import io.atlasmap.v2.Action;
import io.atlasmap.v2.ActionDetail;
import io.atlasmap.v2.Actions;
import io.atlasmap.v2.AtlasMapping;
import io.atlasmap.v2.Capitalize;
import io.atlasmap.v2.CurrentDate;
import io.atlasmap.v2.CurrentDateTime;
import io.atlasmap.v2.CurrentTime;
import io.atlasmap.v2.DataSource;
import io.atlasmap.v2.DataSourceType;
import io.atlasmap.v2.Field;
import io.atlasmap.v2.GenerateUUID;
import io.atlasmap.v2.Lowercase;
import io.atlasmap.v2.Mapping;
import io.atlasmap.v2.MappingType;
import io.atlasmap.v2.Mappings;
import io.atlasmap.v2.PadStringLeft;
import io.atlasmap.v2.PadStringRight;
import io.atlasmap.v2.SeparateByDash;
import io.atlasmap.v2.SeparateByUnderscore;
import io.atlasmap.v2.StringLength;
import io.atlasmap.v2.SubString;
import io.atlasmap.v2.SubStringAfter;
import io.atlasmap.v2.SubStringBefore;
import io.atlasmap.v2.Trim;
import io.atlasmap.v2.TrimLeft;
import io.atlasmap.v2.TrimRight;
import io.atlasmap.v2.Uppercase;

public abstract class AtlasBaseActionsTest extends AtlasMappingBaseTest {

    protected Field sourceField = null;
    protected Field targetField = null;
    protected String docURI = null;
    protected boolean stringLengthTestResultIsInteger = false;

    @Test
    public void testActions() throws Exception {
        List<ActionDetail> actions = DefaultAtlasContextFactory.getInstance().getFieldActionService()
                .listActionDetails();
        for (ActionDetail d : actions) {
            System.out.println(d.getName());
        }

        this.runActionTest(new Uppercase(), "fname", "FNAME");
        this.runActionTest(new Lowercase(), "fnAme", "fname");

        this.runActionTest(new Trim(), " fname ", "fname");
        this.runActionTest(new TrimLeft(), " fname ", "fname ");
        this.runActionTest(new TrimRight(), " fname ", " fname");
        this.runActionTest(new Capitalize(), "fname", "Fname");
        this.runActionTest(new SeparateByDash(), "f:name", "f-name");
        this.runActionTest(new SeparateByUnderscore(), "f-na_me", "f_na_me");

        SubString s = new SubString();
        s.setStartIndex(0);
        s.setEndIndex(3);
        this.runActionTest(s, "12345", "123");

        SubStringAfter s1 = new SubStringAfter();
        s1.setStartIndex(3);
        s1.setEndIndex(null);
        s1.setMatch("foo");
        this.runActionTest(s1, "foobarblah", "blah");

        SubStringBefore s2 = new SubStringBefore();
        s2.setStartIndex(3);
        s2.setEndIndex(null);
        s2.setMatch("blah");
        this.runActionTest(s2, "foobarblah", "bar");

        PadStringRight ps = new PadStringRight();
        ps.setPadCharacter("X");
        ps.setPadCount(5);
        this.runActionTest(ps, "fname", "fnameXXXXX");

        PadStringLeft pl = new PadStringLeft();
        pl.setPadCharacter("X");
        pl.setPadCount(5);
        this.runActionTest(pl, "fname", "XXXXXfname");

        String result = (String) runActionTest(new CurrentDate(), "fname", null);
        assertTrue(Pattern.compile("20([1-9][0-9])-(0[0-9]|1[0-2])-(0[0-9]|1[0-9]|2[0-9]|3[0-1])").matcher(result)
                .matches());

        result = (String) runActionTest(new CurrentTime(), "fname", null);
        assertTrue(Pattern.compile("([01]?[0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]").matcher(result).matches());

        result = (String) runActionTest(new CurrentDateTime(), "fname", null);
        assertTrue(Pattern.compile("20([1-9][0-9])-(0[0-9]|1[0-2])-(0[0-9]|1[0-9]|2[0-9]|3[0-1])T[0-9]{2}:[0-9]{2}Z")
                .matcher(result).matches());

        result = (String) runActionTest(new GenerateUUID(), "fname", null);
        assertTrue(Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}").matcher(result)
                .matches());
    }

    public Object runActionTest(Action action, String inputFirstName, Object outputExpected) throws Exception {
        return this.runActionTestList(Arrays.asList(action), inputFirstName, outputExpected);
    }

    public Object runActionTestList(List<Action> actions, String inputFirstName, Object outputExpected)
            throws Exception {
        System.out.println("Now running test for actions: " + actions);
        System.out.println("Input: " + inputFirstName);
        System.out.println("Expected output: " + outputExpected);

        Mapping m = new Mapping();
        m.setMappingType(MappingType.MAP);
        m.getSourceField().add(this.sourceField);
        m.getTargetField().add(this.targetField);
        if (actions != null) {
            m.getTargetField().get(0).setActions(new Actions());
            m.getTargetField().get(0).getActions().getActions().addAll(actions);
        }

        DataSource src = new DataSource();
        src.setDataSourceType(DataSourceType.SOURCE);
        src.setUri(this.docURI);

        DataSource tgt = new DataSource();
        tgt.setDataSourceType(DataSourceType.TARGET);
        tgt.setUri(this.docURI);

        AtlasMapping atlasMapping = new AtlasMapping();
        atlasMapping.setName("fieldactiontest");
        atlasMapping.setMappings(new Mappings());
        atlasMapping.getMappings().getMapping().add(m);
        atlasMapping.getDataSource().add(src);
        atlasMapping.getDataSource().add(tgt);

        String tmpFile = "target/fieldactions-" + this.getClass().getSimpleName() + "-tmp.txt";
        DefaultAtlasContextFactory.getInstance().getMappingService().saveMappingAsFile(atlasMapping, new File(tmpFile),
                AtlasMappingFormat.XML);

        AtlasContext context = atlasContextFactory.createContext(new File(tmpFile).toURI());
        AtlasSession session = context.createSession();
        session.setSource(createInput(inputFirstName));
        context.process(session);

        Object outputActual = session.getTarget();
        assertNotNull(outputActual);
        outputActual = getOutputValue(outputActual);
        if (outputExpected != null) {
            assertEquals(outputExpected, outputActual);
        }

        return outputActual;
    }

    @Test
    public void runStringLengthTest() throws Exception {
        this.runActionTest(new StringLength(), "fname", stringLengthTestResultIsInteger ? 5 : "5");
    }

    public abstract Object createInput(String sourceFirstName);

    public abstract Object getOutputValue(Object output);
}
