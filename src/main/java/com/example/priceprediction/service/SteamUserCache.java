package com.example.priceprediction.service;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SteamUserCache implements Serializable {
    private String steamId;
    private String nickname;
    private String avatarUrl;
}