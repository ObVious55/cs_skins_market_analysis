package com.example.priceprediction.rag;

import java.util.List;
import java.util.Map;

public class ItemRagDocument {

    private String docId;
    private String docType;

    private List<Long> nameIds;

    private String familyKey;

    private String weapon;
    private String skinCn;
    private String skinEn;
    private String title;
    private String content;

    private Map<String, Object> metadata;

    public String getDocId() {
        return docId;
    }

    public void setDocId(String docId) {
        this.docId = docId;
    }

    public String getDocType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }

    public List<Long> getNameIds() {
        return nameIds;
    }

    public void setNameIds(List<Long> nameIds) {
        this.nameIds = nameIds;
    }

    public String getFamilyKey() {
        return familyKey;
    }

    public void setFamilyKey(String familyKey) {
        this.familyKey = familyKey;
    }

    public String getWeapon() {
        return weapon;
    }

    public void setWeapon(String weapon) {
        this.weapon = weapon;
    }

    public String getSkinCn() {
        return skinCn;
    }

    public void setSkinCn(String skinCn) {
        this.skinCn = skinCn;
    }

    public String getSkinEn() {
        return skinEn;
    }

    public void setSkinEn(String skinEn) {
        this.skinEn = skinEn;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
