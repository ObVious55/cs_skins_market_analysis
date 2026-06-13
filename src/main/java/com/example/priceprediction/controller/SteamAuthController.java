package com.example.priceprediction.controller;

import com.example.priceprediction.service.SteamUserCache;
import com.example.priceprediction.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class SteamAuthController {

    @Autowired
    private UserService userService;

    /**
     * 1. 引导用户跳转到 Steam 进行授权
     */
    @GetMapping("/steam/login")
    public void steamLogin(HttpServletResponse response) throws IOException {
        // 回调地址：Steam 验证成功后会跳回这里
        String returnUrl = "http://localhost:3000/api/auth/steam/callback";
        String realm = "http://localhost:3000";

        String url = "https://steamcommunity.com/openid/login" +
                "?openid.ns=http://specs.openid.net/auth/2.0" +
                "&openid.mode=checkid_setup" +
                "&openid.return_to=" + returnUrl +
                "&openid.realm=" + realm +
                "&openid.identity=http://specs.openid.net/auth/2.0/identifier_select" +
                "&openid.claimed_id=http://specs.openid.net/auth/2.0/identifier_select";

        log.info("引导用户前往 Steam 授权...");
        response.sendRedirect(url);
    }

    /**
     * 2. Steam 授权后的回调接口
     */
    @GetMapping("/steam/callback")
    public void steamCallback(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // 1. 从 Steam 返回的参数中提取身份标识
        String claimedId = request.getParameter("openid.claimed_id");

        if (claimedId == null || !claimedId.contains("/id/")) {
            log.error("Steam 回调参数异常，无法获取身份标识");
            // 失败也跳回首页，并带上失败参数，让前端提示用户
            response.sendRedirect("http://localhost:3000/index.html?loginSuccess=false");
            return;
        }

        // 2. 提取 17 位 SteamID
        String steamId = claimedId.substring(claimedId.lastIndexOf("/") + 1);
        log.info("Steam 身份验证通过，SteamID: {}", steamId);

        // 3. 核心调用：去 Service 抓取资料并存入 Redis
        // 这一步确保了 Redis 中已经有了该用户的昵称和头像
        SteamUserCache user = userService.getAndCacheSteamUser(steamId);

        if (user == null) {
            log.error("获取 Steam 用户资料失败，ID: {}", steamId);
            response.sendRedirect("http://localhost:3000/index.html?loginSuccess=false&error=api_error");
            return;
        }

        // 4. 重定向回前端首页
        // 我们将 steamId 放在 URL 参数中传给前端 index.html
        String redirectUrl = "http://localhost:3000/index.html?loginSuccess=true&steamId=" + steamId;
        log.info("登录成功，正在重定向回首页: {}", redirectUrl);

        response.sendRedirect(redirectUrl);
    }
    /**
     * 3. 获取用户信息接口
     * 前端拿到 URL 里的 steamId 后，会通过 fetch 调用这个接口
     */
    @GetMapping("/user/info")
    public ResponseEntity<?> getUserInfo(String steamId) {
        log.info("前端正在查询 Steam 用户资料，ID: {}", steamId);

        if (steamId == null || steamId.isEmpty()) {
            return ResponseEntity.badRequest().body("SteamID 不能为空");
        }

        // 直接调用现有的 userService
        // 它会自动逻辑：先看 Redis 缓存，没有再去 Steam 查
        SteamUserCache user = userService.getAndCacheSteamUser(steamId);

        if (user != null) {
            // 返回 200 OK 和用户信息 JSON
            return ResponseEntity.ok(user);
        } else {
            // 返回 404
            return ResponseEntity.status(404).body("未找到该用户的缓存资料");
        }
    }
}