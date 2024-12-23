package io.sentrius.sso.core.model.users;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sentrius.sso.core.utils.JsonUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import jakarta.persistence.*;

@Builder
@Entity
@Table(name = "user_settings")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserSettings {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "bg")
    private String bg;

    @Column(name = "fg")
    private String fg;

    // Additional color columns
    @Column(name = "d1")
    private String d1;
    @Column(name = "d2")
    private String d2;
    @Column(name = "d3")
    private String d3;
    @Column(name = "d4")
    private String d4;
    @Column(name = "d5")
    private String d5;
    @Column(name = "d6")
    private String d6;
    @Column(name = "d7")
    private String d7;
    @Column(name = "d8")
    private String d8;
    @Column(name = "b1")
    private String b1;
    @Column(name = "b2")
    private String b2;
    @Column(name = "b3")
    private String b3;
    @Column(name = "b4")
    private String b4;
    @Column(name = "b5")
    private String b5;
    @Column(name = "b6")
    private String b6;
    @Column(name = "b7")
    private String b7;
    @Column(name = "b8")
    private String b8;

    @Column(name = "json_config", columnDefinition = "TEXT") // New JSON config field
    private String jsonConfig;

    @Transient
    private UserConfig settings;

    @Transient
    private String[] colors;

    public UserConfig getSettings() {
        if (settings == null && StringUtils.isNotEmpty(jsonConfig)) {
            try {
                settings = JsonUtil.MAPPER.readValue(jsonConfig, UserConfig.class);
            } catch (Exception e) {
                e.printStackTrace();
                settings = new UserConfig(); // Return an empty instance on failure
            }
        }
        return settings;
    }

    public void setSettings(UserConfig settings) {
        this.settings = settings;
        if (settings != null) {
            try {
                this.jsonConfig = JsonUtil.MAPPER.writeValueAsString(settings);
            } catch (Exception e) {
                e.printStackTrace();
                this.jsonConfig = null; // Clear JSON string on serialization failure
            }
        }
    }


    public String[] getColors() {
        if (colors == null) {
            colors = new String[]{
                d1, d2, d3, d4, d5, d6, d7, d8,
                b1, b2, b3, b4, b5, b6, b7, b8
            };
        }
        return colors;
    }

    public void setColors(String[] colors) {
        if (colors != null && colors.length == 16) {
            this.d1 = colors[0];
            this.d2 = colors[1];
            this.d3 = colors[2];
            this.d4 = colors[3];
            this.d5 = colors[4];
            this.d6 = colors[5];
            this.d7 = colors[6];
            this.d8 = colors[7];
            this.b1 = colors[8];
            this.b2 = colors[9];
            this.b3 = colors[10];
            this.b4 = colors[11];
            this.b5 = colors[12];
            this.b6 = colors[13];
            this.b7 = colors[14];
            this.b8 = colors[15];
        }
    }

    @Transient
    public String getPlane() {
        if (StringUtils.isNotEmpty(bg) && StringUtils.isNotEmpty(fg)) {
            return bg + "," + fg;
        }
        return null;
    }

    public void setPlane(String plane) {
        if (StringUtils.isNotEmpty(plane) && plane.split(",").length == 2) {
            String[] parts = plane.split(",");
            this.bg = parts[0];
            this.fg = parts[1];
        }
    }

    @Transient
    public String getTheme() {
        if (this.colors != null && this.colors.length == 16) {
            return StringUtils.join(this.colors, ",");
        }
        return null;
    }

    public void setTheme(String theme) {
        if (StringUtils.isNotEmpty(theme) && theme.split(",").length == 16) {
            this.setColors(theme.split(","));
        }
    }
}
