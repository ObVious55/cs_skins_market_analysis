package com.example.priceprediction.runner;

import com.example.priceprediction.dto.CsQaqItemIdDto;
import com.example.priceprediction.entity.CsQaqItemIdEntity;
import com.example.priceprediction.repository.CsQaqItemIdRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class CsQaqItemIdJsonImportRunner implements CommandLineRunner {

    private static final int BATCH_SIZE = 1000;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final CsQaqItemIdRepository csQaqItemIdRepository;

    @Value("${cs.qaq-item-id.import-enabled:false}")
    private boolean importEnabled;

    @Value("${cs.qaq-item-id.json-location:classpath*:data/CS_item/steam/饰品id_20260423*.json}")
    private String jsonLocation;

    public CsQaqItemIdJsonImportRunner(CsQaqItemIdRepository csQaqItemIdRepository) {
        this.csQaqItemIdRepository = csQaqItemIdRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("qaq itemId importEnabled = " + importEnabled);
        System.out.println("qaq itemId jsonLocation = " + jsonLocation);

        if (!importEnabled) {
            System.out.println("QAQ 饰品 ID JSON 导入未开启");
            return;
        }

        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources(jsonLocation);

        if (resources.length == 0) {
            System.out.println("没有找到 QAQ 饰品 ID JSON 文件，路径为：" + jsonLocation);
            return;
        }

        System.out.println("找到 QAQ 饰品 ID JSON 文件数量：" + resources.length);

        Set<Long> existingItemIds = new HashSet<>(csQaqItemIdRepository.findAllItemIds());
        System.out.println("数据库已有 item_id 数量：" + existingItemIds.size());

        int totalCount = 0;
        int insertCount = 0;
        int skipCount = 0;

        List<CsQaqItemIdEntity> batchList = new ArrayList<>();

        for (Resource resource : resources) {
            System.out.println("开始导入文件：" + resource.getFilename());

            try (InputStream inputStream = resource.getInputStream()) {
                JsonNode rootNode = objectMapper.readTree(inputStream);

                if (rootNode == null || rootNode.isNull()) {
                    System.out.println("空 JSON 文件，跳过：" + resource.getFilename());
                    continue;
                }

                if (!rootNode.isArray()) {
                    System.out.println("当前文件不是 JSON 数组，跳过：" + resource.getFilename());
                    continue;
                }

                for (JsonNode itemNode : rootNode) {
                    totalCount++;

                    CsQaqItemIdDto dto = objectMapper.treeToValue(itemNode, CsQaqItemIdDto.class);

                    CsQaqItemIdEntity entity = buildEntity(dto, existingItemIds);

                    if (entity == null) {
                        skipCount++;
                    } else {
                        batchList.add(entity);
                        insertCount++;
                        existingItemIds.add(entity.getItemId());
                    }

                    if (batchList.size() >= BATCH_SIZE) {
                        csQaqItemIdRepository.saveAll(batchList);
                        batchList.clear();

                        System.out.println("已扫描：" + totalCount
                                + "，已新增：" + insertCount
                                + "，已跳过：" + skipCount);
                    }
                }

                System.out.println("文件处理完成：" + resource.getFilename());

            } catch (Exception e) {
                System.err.println("导入文件失败：" + resource.getFilename());
                e.printStackTrace();
            }
        }

        if (!batchList.isEmpty()) {
            csQaqItemIdRepository.saveAll(batchList);
            batchList.clear();
        }

        System.out.println("QAQ 饰品 ID JSON 导入完成");
        System.out.println("扫描总数量：" + totalCount);
        System.out.println("新增数量：" + insertCount);
        System.out.println("跳过数量：" + skipCount);
    }

    private CsQaqItemIdEntity buildEntity(CsQaqItemIdDto dto,
                                          Set<Long> existingItemIds) {
        if (dto == null) {
            return null;
        }

        if (dto.getItemId() == null) {
            return null;
        }

        if (!StringUtils.hasText(dto.getName())) {
            return null;
        }

        if (!StringUtils.hasText(dto.getMarketHashName())) {
            return null;
        }

        if (existingItemIds.contains(dto.getItemId())) {
            return null;
        }

        return new CsQaqItemIdEntity(
                dto.getItemId(),
                dto.getName(),
                dto.getMarketHashName()
        );
    }
}
