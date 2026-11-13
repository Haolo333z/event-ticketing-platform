package com.haolo.ticketing;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import com.haolo.ticketing.identity.DemoSettings;
import static org.junit.jupiter.api.Assertions.*;

class DemoAuthConfigurationTest {
    AnnotationConfigApplicationContext context(boolean enabled,String... profiles){
        var context=new AnnotationConfigApplicationContext();context.getEnvironment().setActiveProfiles(profiles);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("fixtures",Map.of("spring.profiles.active",String.join(",",profiles),"demo.auth.enabled",enabled,"demo.auth.customer-token","fixture-customer","demo.auth.admin-token","fixture-admin","logging.level.org.springframework","INFO")));
        context.register(DemoSettings.class);return context;
    }
    @Test void localTestAndTheirCombinationCanStartWithMockAuth(){for(var profiles:List.of(new String[]{"local"},new String[]{"test"},new String[]{"local","test"}))try(var c=context(true,profiles)){c.refresh();assertTrue(c.getBean(DemoSettings.class).safeProfile);assertTrue(c.getBean(DemoSettings.class).enabled);}}
    @Test void absentProductionOrMixedProfilesFailContextStartup(){for(var profiles:List.of(new String[]{},new String[]{"prod"},new String[]{"local","prod"},new String[]{"test","staging"}))try(var c=context(true,profiles)){var error=assertThrows(org.springframework.beans.BeansException.class,c::refresh);assertTrue(error.getMostSpecificCause().getMessage().contains("only in local/test"));}}
    @Test void productionDefaultKeepsMockAuthDisabled(){try(var c=context(false,"prod")){c.refresh();assertFalse(c.getBean(DemoSettings.class).enabled);assertFalse(c.getBean(DemoSettings.class).safeProfile);}}
}
