package com.example.priceprediction.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor

public class InventoryItemDTO {
    private String assetId;    // 唯一实例ID
    private String name;       // 饰品名称（不含磨损后缀）
    private String wear;       // 磨损等级（如：崭新出厂），没有则为"无"
    private int amount;        // 数量
    private String iconUrl;    // 图片URL后缀（用于前端展示）

}
