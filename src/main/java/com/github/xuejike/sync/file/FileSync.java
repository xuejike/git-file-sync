package com.github.xuejike.sync.file;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.github.xuejike.sync.file.config.SyncConfig;
import com.github.xuejike.sync.file.exception.FileSyncException;
import com.github.xuejike.sync.file.utils.AntPathMatcher;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.File;
import java.io.IOException;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;


/**
 * @author xuejike
 * @date 2020/11/27
 */
@Slf4j
public class FileSync {
    private SyncConfig config;
    private Git git;
    private AtomicLong modifyFileCount = new AtomicLong(0);
    private AtomicLong commitCount = new AtomicLong(0);
    private final File syncFolder;
    private final AntPathMatcher matcher;

    public FileSync(SyncConfig config) {
        this.config = config;
        matcher = new AntPathMatcher();
        syncFolder = new File(config.getSyncFolder());
        if (CollUtil.isEmpty(config.getIgnoreList())){
            config.setIgnoreList(new HashSet<>());
        }
    }
    public void exec(){
        if (!FileUtil.exist(config.getSyncFolder())){
            log.info("[{}]-文件夹不存在,通过git进行clone",config.getGroup());
            gitClone();
        }else{
            openGit();
        }

        CommonPool.getPool().scheduleAtFixedRate(this::runSync,
                config.getGitSyncTime(),config.getGitSyncTime(), TimeUnit.MINUTES);
        CommonPool.getPool().scheduleAtFixedRate(this::runCommit,
                0,config.getGitCommitTime(), TimeUnit.MINUTES);
        log.info("[{}]-自动同步开启",config.getGroup());
    }

    private void openGit() {
        try {
            git = Git.open(new File(config.getSyncFolder()));
        } catch (IOException e) {
            if (e instanceof RepositoryNotFoundException){
                try {
                    Git.init().setDirectory(new File(config.getSyncFolder())).call();
                } catch (GitAPIException gitAPIException) {
                    throw new FileSyncException("git init error :"+gitAPIException.getMessage(),gitAPIException);
                }
            }else{
                throw new FileSyncException("git open error :"+e.getMessage(),e);
            }

        }
    }

    public void gitClone(){
        if (StrUtil.isNotBlank(config.getGitUrl())){
            CloneCommand cloneCommand = Git.cloneRepository().setURI(config.getGitUrl())
                    .setDirectory(new File(config.getSyncFolder()));
            if (StrUtil.isNotBlank(config.getGitUsername()) && StrUtil.isNotBlank(config.getGitPwd())){
                cloneCommand.setCredentialsProvider(getCredentialsProvider());
            }
            try {
                git = cloneCommand.call();
            } catch (GitAPIException e) {
                throw new FileSyncException("git clone error:"+e.getMessage(),e);
            }

        }else{
            throw new FileSyncException("clone 失败:git 配置信息缺失");
        }
    }

    public void runSync(){
        if (commitCount.get() > 0){
            try {
                gitPush();
            }catch (Exception ex){
                log.error("[{}]-推送数据失败:{}",config.getGroup(),ex.getMessage(),ex);
            }
        }
        try {
            gitPull();
        }catch (Exception ex){
            log.error("[{}]-更新数据失败,{}",config.getGroup(),ex.getMessage(),ex);
        }
    }
    public void runCommit(){
        try {
            gitCommit();
        }catch (Exception ex){
            log.error("[{}]-自动提交失败:{}",config.getGroup(),ex.getMessage(),ex);
        }
    }



    public void gitCommit(){
        try {
            checkAndDelete();
            Map<String, DiffEntry.ChangeType> diff = getDiff();
            log.info("[{}]-检查差异",config.getGroup());
            if (diff.size() > 0){
                AddCommand add = git.add();
                diff.keySet().forEach(add::addFilepattern);
                add.call();

                CommitCommand commitCommand = git.commit();
                diff.keySet().forEach(commitCommand::setOnly);
                for (Map.Entry<String, DiffEntry.ChangeType> entry : diff.entrySet()) {
                    log.info("[{}]-提交-{} -- > {}",config.getGroup(),entry.getValue(),entry.getKey());
                }

                commitCommand.setMessage(StrUtil.format("自动提交时间:{},修改文件数:{}",new DateTime(),diff)).call();
            }
        } catch (GitAPIException e) {
            throw new FileSyncException(e);
        }
    }
    public void gitPush(){
        try {
            log.info("git push {}",commitCount.get());
            git.push().setCredentialsProvider(getCredentialsProvider()).call();
        } catch (GitAPIException e) {
            throw new FileSyncException(e);
        }
    }
    public void gitPull(){
        try {
            log.info("[{}]-git pull",config.getGroup());
            PullResult call = git.pull().setCredentialsProvider(getCredentialsProvider()).call();
            FetchResult fetchResult = call.getFetchResult();


        } catch (GitAPIException e) {
            throw new FileSyncException(e);
        }
    }


    public void checkAndDelete(){
        Status call = null;
        try {
            log.info("[{}]-检查缺失文件",config.getGroup());
            call = git.status().call();
            Set<String> missing = call.getMissing();
            if (CollUtil.isEmpty(missing)){
                return;
            }
            RmCommand rmCommand = git.rm();
            for (String s : missing) {
                rmCommand.addFilepattern(s);
                log.info("[{}]-删除文件->{}",config.getGroup(),s);
            }
            rmCommand.call();
            CommitCommand commit = git.commit();
            missing.forEach(commit::setOnly);
            commit.setMessage(StrUtil.format("自动删除文件-{}",new DateTime())).call();
        } catch (GitAPIException e) {
            log.error("[{}]-检查删除文件错误",config.getGroup(),e);
        }
    }

    public Map<String, DiffEntry.ChangeType> getDiff() throws GitAPIException {
        List<DiffEntry> diffEntryList = git.diff().call();
        Map<String, DiffEntry.ChangeType> map = new HashMap<>(diffEntryList.size());
        for (DiffEntry entry : diffEntryList) {
            switch (entry.getChangeType()){
                case DELETE:
                    map.put(entry.getOldPath(), entry.getChangeType());
                    break;
                case ADD:
                    map.put(entry.getNewPath(), entry.getChangeType());
                    break;
                case COPY:
                    map.put(entry.getNewPath(), entry.getChangeType());
                    break;
                case MODIFY:
                    map.put(entry.getOldPath(), entry.getChangeType());
                    break;
                case RENAME:
                    map.put(entry.getNewPath(), entry.getChangeType());
                    break;
                default:{

                }
            }
        }

        return map;

    }

    private UsernamePasswordCredentialsProvider getCredentialsProvider() {
        return new UsernamePasswordCredentialsProvider(config.getGitUsername(), config.getGitPwd());
    }


    protected boolean isIgnore(String  checkFile){
        if (CollUtil.isEmpty(config.getIgnoreList()) ){
            return false;
        }
        for (String ant : config.getIgnoreList()) {
            if (matcher.match(ant,checkFile)){
                log.debug("[{}]忽略规则匹配成功:{}->{}",config.getGroup(),ant,checkFile);
                return true;
            }
        }
        return false;
    }



}
