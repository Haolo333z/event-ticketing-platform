package com.haolo.ticketing.identity;

import javax.sql.DataSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;

@Service
public class DemoSeedService {
    private final DataSource dataSource;
    private final DemoSettings settings;
    public DemoSeedService(DataSource dataSource, DemoSettings settings) { this.dataSource=dataSource; this.settings=settings; }
    @Transactional
    public void seed() {
        if(!settings.safeProfile) throw new IllegalStateException("Seed is limited to local/test");
        var populator=new ResourceDatabasePopulator(new ClassPathResource("db/demo/seed.sql"));
        populator.setSqlScriptEncoding("UTF-8");
        populator.execute(dataSource);
    }
}
