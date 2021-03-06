package lee.study.down.controller;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import lee.study.down.HttpDownServer;
import lee.study.down.dispatch.DefaultHttpDownCallback;
import lee.study.down.form.DownForm;
import lee.study.down.model.ChunkInfo;
import lee.study.down.model.DirInfo;
import lee.study.down.model.HttpDownInfo;
import lee.study.down.model.ResultInfo;
import lee.study.down.model.ResultInfo.ResultStatus;
import lee.study.down.model.TaskInfo;
import lee.study.down.util.HttpDownUtil;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HttpDownController {

  @RequestMapping("/getTask")
  public ResultInfo getTask(@RequestParam String id) throws Exception {
    ResultInfo resultInfo = new ResultInfo();
    HttpDownInfo httpDownInfo = HttpDownServer.DOWN_CONTENT.get(id);
    if (httpDownInfo == null) {
      resultInfo.setStatus(ResultStatus.BAD.getCode()).setMsg("任务不存在");
    } else {
      resultInfo.setData(httpDownInfo.getTaskInfo());
    }
    return resultInfo;
  }

  @RequestMapping("/getTaskList")
  public ResultInfo getTaskList() {
    ResultInfo resultInfo = new ResultInfo();
    if (HttpDownServer.DOWN_CONTENT != null && HttpDownServer.DOWN_CONTENT.size() > 0) {
      List<TaskInfo> taskInfoList = new ArrayList<>();
      for (Object key : HttpDownServer.DOWN_CONTENT.keySet().stream().sorted().toArray()) {
        HttpDownInfo httpDownModel = HttpDownServer.DOWN_CONTENT.get(key);
        if (httpDownModel.getTaskInfo().getStatus() != 0) {
          taskInfoList.add(httpDownModel.getTaskInfo());
        }
      }
      resultInfo.setData(taskInfoList);
    }
    return resultInfo;
  }

  @RequestMapping("/startTask")
  public ResultInfo startTask(@RequestBody DownForm downForm) throws Exception {
    ResultInfo resultInfo = new ResultInfo();
    if (StringUtils.isEmpty(downForm.getFileName())) {
      resultInfo.setStatus(ResultStatus.BAD.getCode()).setMsg("文件名不能为空");
      return resultInfo;
    }
    if (StringUtils.isEmpty(downForm.getPath())) {
      resultInfo.setStatus(ResultStatus.BAD.getCode()).setMsg("路径不能为空");
      return resultInfo;
    }
    if (!new File(downForm.getPath()).exists()) {
      resultInfo.setStatus(ResultStatus.BAD.getCode()).setMsg("路径不存在");
      return resultInfo;
    }
    HttpDownInfo httpDownModel = HttpDownServer.DOWN_CONTENT.get(downForm.getId());
    TaskInfo taskInfo = httpDownModel.getTaskInfo();
    synchronized (taskInfo) {
      if (taskInfo.getChunkInfoList() == null) {
        //有下载中的文件同名
        if (HttpDownServer.DOWN_CONTENT.values().stream().anyMatch(
            (httpDownInfo -> !httpDownInfo.getTaskInfo().getId().equals(downForm.getId())
                && httpDownInfo.getTaskInfo().getStatus() !=0 && downForm.getFileName()
                .equals(httpDownInfo.getTaskInfo().getFileName())))) {
          resultInfo.setStatus(ResultStatus.BAD.getCode()).setMsg("文件名已存在，请修改");
          return resultInfo;
        }
        taskInfo.setFileName(downForm.getFileName());
        taskInfo.setFilePath(downForm.getPath());
        List<ChunkInfo> chunkInfoList = new ArrayList<>();
        if (taskInfo.getTotalSize() > 0) {  //非chunked编码
          if (taskInfo.isSupportRange()) {
            taskInfo.setConnections(downForm.getConnections());
          }
          //计算chunk列表
          for (int i = 0; i < downForm.getConnections(); i++) {
            ChunkInfo chunkInfo = new ChunkInfo();
            chunkInfo.setIndex(i);
            long chunkSize = taskInfo.getTotalSize() / downForm.getConnections();
            chunkInfo.setOriStartPosition(i * chunkSize);
            chunkInfo.setNowStartPosition(chunkInfo.getOriStartPosition());
            if (i == downForm.getConnections() - 1) { //最后一个连接去下载多出来的字节
              chunkSize += taskInfo.getTotalSize() % downForm.getConnections();
            }
            chunkInfo.setEndPosition(chunkInfo.getOriStartPosition() + chunkSize - 1);
            chunkInfo.setTotalSize(chunkSize);
            chunkInfoList.add(chunkInfo);
          }
        } else { //chunked下载
          ChunkInfo chunkInfo = new ChunkInfo();
          chunkInfo.setIndex(0);
          chunkInfo.setNowStartPosition(0);
          chunkInfo.setOriStartPosition(0);
          chunkInfoList.add(chunkInfo);
        }
        taskInfo.setChunkInfoList(chunkInfoList);
        HttpDownUtil.taskDown(httpDownModel, new DefaultHttpDownCallback());
      } else {
        resultInfo.setStatus(ResultStatus.BAD.getCode()).setMsg("任务正在下载中");
      }
    }
    return resultInfo;
  }

  @RequestMapping("/getChildDirList")
  public ResultInfo getChildDirList(@RequestBody(required = false) DirInfo body) {
    ResultInfo resultInfo = new ResultInfo();
    List<DirInfo> data = new LinkedList<>();
    resultInfo.setData(data);
    File[] files;
    if (body == null || StringUtils.isEmpty(body.getPath())) {
      files = File.listRoots();
    } else {
      File file = new File(body.getPath());
      if (file.exists() && file.isDirectory()) {
        files = file.listFiles();
      } else {
        resultInfo.setStatus(ResultStatus.BAD.getCode()).setMsg("路径不存在");
        return resultInfo;
      }
    }
    if (files != null && files.length > 0) {
      for (File tempFile : files) {
        if (tempFile.isDirectory() && (tempFile.getParent() == null || !tempFile.isHidden())) {
          DirInfo dirInfo = new DirInfo(
              StringUtils.isEmpty(tempFile.getName()) ? tempFile.getAbsolutePath()
                  : tempFile.getName(), tempFile.getAbsolutePath(),
              tempFile.listFiles() == null ? true : Arrays.stream(tempFile.listFiles())
                  .noneMatch(file ->
                      file != null && file.isDirectory() && !file.isHidden()
                  ));
          data.add(dirInfo);
        }
      }
    }
    return resultInfo;
  }

}
