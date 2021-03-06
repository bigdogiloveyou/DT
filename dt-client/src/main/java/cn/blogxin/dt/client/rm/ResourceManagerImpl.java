package cn.blogxin.dt.client.rm;

import cn.blogxin.dt.api.context.DTParam;
import cn.blogxin.dt.client.context.ActionContext;
import cn.blogxin.dt.client.context.DTContext;
import cn.blogxin.dt.client.context.DTContextEnum;
import cn.blogxin.dt.client.log.entity.Action;
import cn.blogxin.dt.client.log.enums.ActionStatus;
import cn.blogxin.dt.client.log.repository.ActionRepository;
import cn.blogxin.dt.client.util.Utils;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.springframework.dao.DuplicateKeyException;

import javax.annotation.Resource;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * @author kris
 */
@Slf4j
public class ResourceManagerImpl implements ResourceManager {

    private static final Map<String, ActionResource> RESOURCES = Maps.newConcurrentMap();

    @Resource
    private ActionRepository actionRepository;

    @Override
    public void registerResource(ActionResource resource) {
        RESOURCES.put(resource.getActionName(), resource);
    }

    @Override
    public void registerAction(Action action) {
        ActionContext actionContext = DTContext.get(DTContextEnum.ACTION_CONTEXT);
        actionContext.put(action.getName(), action);
        try {
            actionRepository.insert(action);
        } catch (DuplicateKeyException e) {
        }
    }

    @Override
    public boolean commitAction() {
        return doAction(ActionStatus.COMMIT);
    }

    @Override
    public boolean rollbackAction() {
        return doAction(ActionStatus.ROLLBACK);
    }

    private boolean doAction(ActionStatus actionStatus) {
        boolean result = true;
        ActionContext actionContext = DTContext.get(DTContextEnum.ACTION_CONTEXT);
        Map<String, Action> actionMap = actionContext.getActionMap();
        if (MapUtils.isNotEmpty(actionMap)) {
            for (Map.Entry<String, Action> entry : actionMap.entrySet()) {
                ActionResource actionResource = RESOURCES.get(entry.getKey());
                if (!execute(entry.getValue(), actionResource, actionStatus)) {
                    result = false;
                }
            }
        }
        return result;
    }

    private boolean execute(Action action, ActionResource actionResource, ActionStatus actionStatus) {
        try {
            DTParam dtParam = new DTParam();
            dtParam.setXid(DTContext.get(DTContextEnum.XID));
            dtParam.setStartTime(DTContext.get(DTContextEnum.START_TIME));
            Method twoPhaseMethod = Utils.getTwoPhaseMethodByActionStatus(actionResource, actionStatus);
            Object[] args = Utils.getTwoPhaseMethodParam(twoPhaseMethod, action.getArguments(), dtParam);
            //todo test
//            if (action.getName().contains("Coupon")) {
//                int i = 10 / 0;
//            }
            twoPhaseMethod.invoke(actionResource.getActionBean(), args);
            //todo test
//            if (action.getName().contains("Coupon")) {
//                int i = 10 / 0;
//            }
            actionRepository.updateStatus(action.getXid(), action.getName(), ActionStatus.INIT, actionStatus);
            //todo test
//            if (action.getName().contains("Coupon")) {
//                int i = 10 / 0;
//            }
            return true;
        } catch (Exception e) {
            log.error("执行Action二阶段失败，等待重试。xid={}，action={}", DTContext.get(DTContextEnum.XID), action, e);
        }
        return false;
    }

}
