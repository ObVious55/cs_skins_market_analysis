package com.example.priceprediction.controller;

import com.example.priceprediction.dto.InventoryItemDTO;
import com.example.priceprediction.service.InventoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    @Autowired
    private InventoryService inventoryService;

    /**
     * 接口 1：获取库存 (秒开)
     * 前端一进页面就调用这个，直接查 MySQL
     */
    @GetMapping("/me")
    public ResponseEntity<List<InventoryItemDTO>> getMyInventory(@RequestParam String steamId) {
        List<InventoryItemDTO> inventory = inventoryService.getUserInventoryFromDb(steamId);
        return ResponseEntity.ok(inventory);
    }

    /**
     * 接口 2：强制同步 (点按钮才触发)
     * 前端点击“刷新库存”按钮时调用，去爬取 Steam API
     */
    @PostMapping("/refresh")
    public ResponseEntity<String> refreshMyInventory(@RequestParam String steamId) {
        try {
            boolean success = inventoryService.refreshInventory(steamId);
            if (!success) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("库存刷新任务正在进行中，请稍后再试。");
            }
            return ResponseEntity.ok("库存同步请求已提交！");
        } catch (RuntimeException e) {
            if ("PRIVATE_INVENTORY".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("您的库存未公开，请前往 Steam 设置为公开。");
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("同步失败，请稍后再试。");
        }
    }
}