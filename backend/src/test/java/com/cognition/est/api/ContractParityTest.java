package com.cognition.est.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * contracts/openapi.yaml is the source of truth: every documented path must exist at runtime and
 * vice versa (local profile, dev endpoints included).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ContractParityTest {
    @Autowired MockMvc mvc;

    @Test
    void runtimePathsMatchTheContract() throws Exception {
        String docs =
                mvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();
        var runtime = new TreeSet<String>();
        JsonMapper.shared().readTree(docs).get("paths").propertyNames().forEach(runtime::add);

        var contract = new TreeSet<String>();
        Pattern path = Pattern.compile("^  (/[^:]+):\\s*$");
        for (String line : Files.readAllLines(Path.of("..", "contracts", "openapi.yaml"))) {
            Matcher m = path.matcher(line);
            if (m.find()) contract.add("/api/v1" + m.group(1));
        }
        assertThat(runtime)
                .as("paths at runtime but not in contracts/openapi.yaml")
                .containsAll(contract);
        assertThat(contract)
                .as("paths in contracts/openapi.yaml but not at runtime")
                .containsAll(runtime);
    }
}
