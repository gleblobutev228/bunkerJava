package com.game.bunker.characteristic.entity;

public class UserCharacteristic {
    private String value;
    private boolean visible;
    private String description;

    public UserCharacteristic() {
    }

    public UserCharacteristic(String value, boolean visible) {
        this.value = value;
        this.visible = visible;
    }

    public UserCharacteristic(String value, boolean visible, String description) {
        this.value = value;
        this.visible = visible;
        this.description = description;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
