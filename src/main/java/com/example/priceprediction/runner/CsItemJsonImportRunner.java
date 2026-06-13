package com.example.priceprediction.runner;

import com.example.priceprediction.dto.CsItemRawDto;
import com.example.priceprediction.entity.CsItemRawEntity;
import com.example.priceprediction.repository.CsItemRawRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.*;

@Component
public class CsItemJsonImportRunner implements CommandLineRunner {

    private static final int BATCH_SIZE = 1000;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final CsItemRawRepository csItemRawRepository;

    @Value("${cs.item.import-enabled:false}")
    private boolean importEnabled;

    @Value("${cs.item.json-location:classpath*:data/CS_item/steam/*.json}")
    private String jsonLocation;

    public CsItemJsonImportRunner(CsItemRawRepository csItemRawRepository) {
        this.csItemRawRepository = csItemRawRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("importEnabled = " + importEnabled);
        System.out.println("jsonLocation = " + jsonLocation);

        if (!importEnabled) {
            System.out.println("CS 饰品 JSON 导入未开启");
            return;
        }

        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources(jsonLocation);

        if (resources.length == 0) {
            System.out.println("没有找到 JSON 文件，路径为：" + jsonLocation);
            return;
        }

        System.out.println("找到 JSON 文件数量：" + resources.length);

        Set<Long> existingNameIds = new HashSet<>(csItemRawRepository.findAllNameIds());
        System.out.println("数据库已有 name_id 数量：" + existingNameIds.size());

        int totalCount = 0;
        int insertCount = 0;
        int skipCount = 0;

        List<CsItemRawEntity> batchList = new ArrayList<>();

        for (Resource resource : resources) {
            System.out.println("开始导入文件：" + resource.getFilename());

            try (InputStream inputStream = resource.getInputStream()) {
                System.out.println("正在读取 JSON 文件内容：" + resource.getFilename());

                JsonNode rootNode = objectMapper.readTree(inputStream);

                System.out.println("JSON 文件读取完成：" + resource.getFilename());
                System.out.println("rootNode 类型：" + rootNode.getNodeType());

                if (rootNode == null || rootNode.isNull()) {
                    System.out.println("空 JSON 文件，跳过：" + resource.getFilename());
                    continue;
                }

                if (rootNode.isObject()) {
                    Iterator<Map.Entry<String, JsonNode>> fields = rootNode.fields();

                    while (fields.hasNext()) {
                        totalCount++;

                        Map.Entry<String, JsonNode> entry = fields.next();
                        String mapKey = entry.getKey();
                        JsonNode itemNode = entry.getValue();

                        CsItemRawDto dto = objectMapper.treeToValue(itemNode, CsItemRawDto.class);

                        CsItemRawEntity entity = buildEntity(mapKey, dto, existingNameIds);

                        if (entity == null) {
                            skipCount++;
                        } else {
                            batchList.add(entity);
                            insertCount++;
                            existingNameIds.add(entity.getNameId());
                        }

                        if (batchList.size() >= BATCH_SIZE) {
                            csItemRawRepository.saveAll(batchList);
                            batchList.clear();

                            System.out.println("已扫描：" + totalCount
                                    + "，已新增：" + insertCount
                                    + "，已跳过：" + skipCount);
                        }
                    }
                } else if (rootNode.isArray()) {
                    for (JsonNode itemNode : rootNode) {
                        totalCount++;

                        CsItemRawDto dto = objectMapper.treeToValue(itemNode, CsItemRawDto.class);
                        String mapKey = dto == null ? null : dto.getEnName();

                        CsItemRawEntity entity = buildEntity(mapKey, dto, existingNameIds);

                        if (entity == null) {
                            skipCount++;
                        } else {
                            batchList.add(entity);
                            insertCount++;
                            existingNameIds.add(entity.getNameId());
                        }

                        if (batchList.size() >= BATCH_SIZE) {
                            csItemRawRepository.saveAll(batchList);
                            batchList.clear();

                            System.out.println("已扫描：" + totalCount
                                    + "，已新增：" + insertCount
                                    + "，已跳过：" + skipCount);
                        }
                    }
                } else {
                    System.out.println("不支持的 JSON 格式，跳过文件：" + resource.getFilename());
                }

                System.out.println("文件处理完成：" + resource.getFilename());

            } catch (Exception e) {
                System.err.println("导入文件失败：" + resource.getFilename());
                e.printStackTrace();
            }
        }

        if (!batchList.isEmpty()) {
            csItemRawRepository.saveAll(batchList);
            batchList.clear();
        }

        System.out.println("CS 饰品 JSON 导入完成");
        System.out.println("扫描总数量：" + totalCount);
        System.out.println("新增数量：" + insertCount);
        System.out.println("跳过数量：" + skipCount);
    }

    private CsItemRawEntity buildEntity(String mapKey,
                                        CsItemRawDto dto,
                                        Set<Long> existingNameIds) {
        if (dto == null) {
            return null;
        }

        if (dto.getNameId() == null) {
            return null;
        }

        if (!StringUtils.hasText(dto.getEnName())) {
            return null;
        }

        if (!StringUtils.hasText(dto.getCnName())) {
            return null;
        }

        if (existingNameIds.contains(dto.getNameId())) {
            return null;
        }

        String finalMapKey = StringUtils.hasText(mapKey) ? mapKey : dto.getEnName();

        return new CsItemRawEntity(
                finalMapKey,
                dto.getEnName(),
                dto.getCnName(),
                dto.getNameId()
        );
    }
}