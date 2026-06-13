package com.example.priceprediction.strategy.yaml;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class StrategyYamlLoader {

    private final ObjectMapper yamlMapper;

    public StrategyYamlLoader() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.yamlMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        this.yamlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public StrategyYamlDefinition load(String strategyName) {
        String path = "strategies/" + strategyName + ".yaml";

        try {
            ClassPathResource resource = new ClassPathResource(path);
            if (resource.exists()) {
                return readResource(resource, path);
            }

            Path filePath = resolveStrategyFile(strategyName + ".yaml");
            if (filePath != null) {
                return readResource(new FileSystemResource(filePath), filePath.toString());
            }

            throw new IllegalArgumentException("策略 YAML 不存在: " + path);
        } catch (Exception e) {
            throw new RuntimeException("读取策略 YAML 失败: " + path + ", error=" + e.getMessage(), e);
        }
    }

    public List<StrategyYamlDefinition> loadAll() {
        Map<String, StrategyYamlDefinition> result = new LinkedHashMap<>();

        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath*:strategies/*.yaml");

            for (Resource resource : resources) {
                readIntoResult(result, resource, resource.getFilename(), false);
            }

            for (Path yamlPath : findFileSystemStrategyFiles()) {
                readIntoResult(result, new FileSystemResource(yamlPath), yamlPath.toString(), true);
            }
        } catch (Exception e) {
            throw new RuntimeException("扫描 strategies/*.yaml 失败: " + e.getMessage(), e);
        }

        return new ArrayList<>(result.values());
    }

    private void readIntoResult(
            Map<String, StrategyYamlDefinition> result,
            Resource resource,
            String path,
            boolean keepExisting
    ) {
        try {
            StrategyYamlDefinition definition = readResource(resource, path);
            if (keepExisting) {
                result.putIfAbsent(definition.getName(), definition);
            } else {
                result.put(definition.getName(), definition);
            }
            log.info("加载策略 YAML 成功: name={}, file={}", definition.getName(), path);
        } catch (Exception e) {
            log.warn("加载策略 YAML 失败: file={}, error={}", path, e.getMessage(), e);
        }
    }

    private StrategyYamlDefinition readResource(Resource resource, String path) throws Exception {
        try (InputStream inputStream = resource.getInputStream()) {
            StrategyYamlDefinition definition =
                    yamlMapper.readValue(inputStream, StrategyYamlDefinition.class);
            validate(definition, path);
            return definition;
        }
    }

    private void validate(StrategyYamlDefinition definition, String path) {
        if (definition == null) {
            throw new IllegalArgumentException("策略 YAML 为空: " + path);
        }
        if (isBlank(definition.getName())) {
            throw new IllegalArgumentException("策略 YAML 缺少 name: " + path);
        }
        if (isBlank(definition.getDisplayName())) {
            throw new IllegalArgumentException("策略 YAML 缺少 display_name: " + path);
        }
        if (isBlank(definition.getDescription())) {
            throw new IllegalArgumentException("策略 YAML 缺少 description: " + path);
        }
        if (isBlank(definition.getInstructions())) {
            throw new IllegalArgumentException("策略 YAML 缺少 instructions: " + path);
        }
    }

    private Path resolveStrategyFile(String filename) {
        return strategyDirectories().stream()
                .map(directory -> directory.resolve(filename))
                .filter(Files::isRegularFile)
                .findFirst()
                .orElse(null);
    }

    private List<Path> findFileSystemStrategyFiles() {
        List<Path> yamlFiles = new ArrayList<>();

        for (Path directory : strategyDirectories()) {
            if (!Files.isDirectory(directory)) {
                continue;
            }

            try (var stream = Files.list(directory)) {
                stream.filter(path -> Files.isRegularFile(path)
                                && path.getFileName().toString().endsWith(".yaml"))
                        .sorted()
                        .forEach(yamlFiles::add);
            } catch (Exception e) {
                log.warn("扫描策略目录失败: directory={}, error={}", directory, e.getMessage(), e);
            }
        }

        return yamlFiles;
    }

    private List<Path> strategyDirectories() {
        return List.of(
                Path.of("strategies"),
                Path.of("src", "strategies")
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
