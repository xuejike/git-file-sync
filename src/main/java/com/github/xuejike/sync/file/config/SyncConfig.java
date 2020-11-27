package com.github.xuejike.sync.file.config;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.setting.Setting;
import lombok.Data;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author xuejike
 * @date 2020/11/27
 */
@Data
public class SyncConfig {
    private String group;
    private String gitUrl;
    private String gitUsername;
    private String gitPwd;
    private Integer gitCommitTime=1;
    private Integer gitSyncTime=2;
    private String syncFolder;
    private Set<String> ignoreList;

    public static SyncConfig parse(Setting setting,String group){
        SyncConfig syncConfig = new SyncConfig();
        syncConfig.setGroup(group);
        syncConfig.setGitUrl(setting.getStr("git.url", group, ""));
        syncConfig.setGitUsername(setting.getStr("git.username", group, ""));
        syncConfig.setGitPwd(setting.getStr("git.password", group, ""));
        syncConfig.setGitCommitTime(setting.getInt("git.commit-time", group, 5));
        syncConfig.setGitSyncTime(setting.getInt("git.sync-time", group, 30));
        syncConfig.setSyncFolder(setting.getStr("sync.folder", group, ""));
        syncConfig.setIgnoreList(new HashSet<>(CollUtil.newArrayList(setting.getStrings("sync.ignore", group, ","))));

        return syncConfig;
    }
}
