package com.github.xuejike.sync.file;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.setting.Setting;
import com.github.xuejike.sync.file.config.SyncConfig;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;

/**
 * @author xuejike
 * @date 2020/11/27
 */
@Slf4j
public class FileSyncMain {
    public static void main(String[] args) {



        Setting setting = new Setting(new File("sync.conf"), StandardCharsets.UTF_8,false);
        List<String> groups = setting.getGroups();
        for (String group : groups) {

            log.info("加载分组:{}",group);
            SyncConfig syncConfig = SyncConfig.parse(setting,group);
            FileSync fileSync = new FileSync(syncConfig);
            fileSync.exec();
        }
    }
}
