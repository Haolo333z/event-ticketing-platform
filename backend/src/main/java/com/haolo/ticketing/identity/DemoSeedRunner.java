package com.haolo.ticketing.identity;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local","test"})
@ConditionalOnProperty(name="demo.seed.enabled",havingValue="true",matchIfMissing=true)
public class DemoSeedRunner implements ApplicationRunner {
    private final DemoSeedService service;
    public DemoSeedRunner(DemoSeedService service){this.service=service;}
    public void run(ApplicationArguments args){service.seed();}
}
