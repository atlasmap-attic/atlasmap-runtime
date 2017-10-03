package io.atlasmap.core.issue;

import static org.mockito.Mockito.*;

import java.net.URL;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import io.atlasmap.api.AtlasContext;
import io.atlasmap.api.AtlasSession;
import io.atlasmap.core.AtlasMappingService;
import io.atlasmap.core.AtlasMappingService.AtlasMappingFormat;
import io.atlasmap.core.DefaultAtlasContextFactory;
import io.atlasmap.v2.AtlasMapping;
import io.syndesis.connector.salesforce.Contact;
import twitter4j.Status;
import twitter4j.User;

public class CamelAtlasmap14Test {

    private AtlasMappingService mappingService;
    
    @Before
    public void before() throws Exception {
        mappingService = DefaultAtlasContextFactory.getInstance().getMappingService();
    }
    
    @Test
    public void test() throws Exception {
        URL url = Thread.currentThread().getContextClassLoader().getResource("mappings/issue-camel-atlasmap-14-mapping.json");
        AtlasMapping mapping = mappingService.loadMapping(url, AtlasMappingFormat.JSON);
        AtlasContext context = DefaultAtlasContextFactory.getInstance().createContext(mapping);
        AtlasSession session = context.createSession();
        session.setSource(generateTwitterStatus());
        context.process(session);
        Object target = session.getTarget();
        Assert.assertEquals(Contact.class, target.getClass());
        Contact contact = (Contact)target;
        Assert.assertEquals("bobvila1982", contact.getTwitterScreenName__c());
    }
    
    protected Status generateTwitterStatus() {
        Status status = mock(Status.class);
        User user = mock(User.class);
        when(user.getName()).thenReturn("Bob Vila");
        when(user.getScreenName()).thenReturn("bobvila1982");
        when(status.getUser()).thenReturn(user);
        when(status.getText()).thenReturn("Let's build a house!");
        return status;
    }
}
