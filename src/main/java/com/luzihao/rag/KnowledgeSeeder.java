package com.luzihao.rag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class KnowledgeSeeder implements ApplicationRunner {
    @Autowired
    private KnowledgeService knowledgeService;


    @Override
    public void run(ApplicationArguments args) throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        for (Resource resource : resolver.getResources("classpath:knowledge/*.md")) {
            String filename = resource.getFilename();
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            knowledgeService.ingest("T1",filename,content,"PUBLIC","classpath:knowledge/" + filename);
        }


    }
}
