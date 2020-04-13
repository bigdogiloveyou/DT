package cn.blogxin.dt.client.rm;

import cn.blogxin.dt.client.context.ActionContext;
import cn.blogxin.dt.client.context.DTContext;
import cn.blogxin.dt.client.context.DTContextEnum;
import cn.blogxin.dt.client.log.entity.Action;
import cn.blogxin.dt.client.log.enums.ActionStatus;
import cn.blogxin.dt.client.log.repository.ActionRepository;
import com.google.common.collect.Maps;
import org.apache.commons.collections.MapUtils;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 资源管理器。控制分支事务，负责分支注册、状态汇报，驱动分支事务的提交和回滚。
 *
 * @author kris
 */
public class ResourceManager {

    private static final Map<String, ActionResource> RESOURCES = Maps.newConcurrentMap();

    @Resource
    private ActionRepository actionRepository;

    public static void registerResource(ActionResource resource) {
        RESOURCES.put(resource.getActionName(), resource);
    }

    public void registerAction(Action action) {
        actionRepository.insert(action);
    }

    public void commitAction() {
        doAction(ActionStatus.COMMIT);
    }

    public void rollbackAction() {
        doAction(ActionStatus.ROLLBACK);
    }

    private void doAction(ActionStatus actionStatus) {
        ActionContext actionContext = DTContext.get(DTContextEnum.ACTION_CONTEXT);
        Map<String, Action> actionMap = actionContext.getActionMap();
        if (MapUtils.isNotEmpty(actionMap)) {
            for (Map.Entry<String, Action> entry : actionMap.entrySet()) {
                Action action = entry.getValue();
                ActionResource actionResource = RESOURCES.get(action.getName());
                Object actionBean = actionResource.getActionBean();
                //todo 执行二阶段 需要先处理dubbo的springbean的注册问题
                actionRepository.updateStatus(action.getXid(), action.getName(), action.getStatus(), actionStatus.getStatus());
            }
        }
    }

}
