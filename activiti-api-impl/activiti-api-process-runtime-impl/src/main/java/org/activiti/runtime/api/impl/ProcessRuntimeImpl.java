/*
 * Copyright 2018 Alfresco, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.activiti.runtime.api.impl;

import java.util.List;
import java.util.Map;

import org.activiti.api.model.shared.model.VariableInstance;
import org.activiti.api.process.model.ProcessDefinition;
import org.activiti.api.process.model.ProcessDefinitionMeta;
import org.activiti.api.process.model.ProcessInstance;
import org.activiti.api.process.model.ProcessInstanceMeta;
import org.activiti.api.process.model.builders.ProcessPayloadBuilder;
import org.activiti.api.process.model.payloads.*;
import org.activiti.api.process.runtime.ProcessRuntime;
import org.activiti.api.process.runtime.conf.ProcessRuntimeConfiguration;
import org.activiti.api.runtime.shared.NotFoundException;
import org.activiti.api.runtime.shared.query.Page;
import org.activiti.api.runtime.shared.query.Pageable;
import org.activiti.engine.ActivitiObjectNotFoundException;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.repository.ProcessDefinitionQuery;
import org.activiti.runtime.api.model.impl.APIProcessDefinitionConverter;
import org.activiti.runtime.api.model.impl.APIProcessInstanceConverter;
import org.activiti.runtime.api.model.impl.APIVariableInstanceConverter;
import org.activiti.api.runtime.model.impl.ProcessDefinitionMetaImpl;
import org.activiti.api.runtime.model.impl.ProcessInstanceImpl;
import org.activiti.api.runtime.model.impl.ProcessInstanceMetaImpl;
import org.activiti.runtime.api.query.impl.PageImpl;
import org.activiti.core.common.spring.security.policies.ActivitiForbiddenException;
import org.activiti.core.common.spring.security.policies.ProcessSecurityPoliciesManager;
import org.activiti.core.common.spring.security.policies.SecurityPolicyAccess;
import org.springframework.security.access.prepost.PreAuthorize;

@PreAuthorize("hasRole('ACTIVITI_USER')")
public class ProcessRuntimeImpl implements ProcessRuntime {

    private final RepositoryService repositoryService;

    private final APIProcessDefinitionConverter processDefinitionConverter;

    private final RuntimeService runtimeService;

    private final APIProcessInstanceConverter processInstanceConverter;

    private final APIVariableInstanceConverter variableInstanceConverter;

    private final ProcessRuntimeConfiguration configuration;

    private final ProcessSecurityPoliciesManager securityPoliciesManager;

    public ProcessRuntimeImpl(RepositoryService repositoryService,
                              APIProcessDefinitionConverter processDefinitionConverter,
                              RuntimeService runtimeService,
                              ProcessSecurityPoliciesManager securityPoliciesManager,
                              APIProcessInstanceConverter processInstanceConverter,
                              APIVariableInstanceConverter variableInstanceConverter,
                              ProcessRuntimeConfiguration configuration) {
        this.repositoryService = repositoryService;
        this.processDefinitionConverter = processDefinitionConverter;
        this.runtimeService = runtimeService;
        this.securityPoliciesManager = securityPoliciesManager;
        this.processInstanceConverter = processInstanceConverter;
        this.variableInstanceConverter = variableInstanceConverter;
        this.configuration = configuration;
    }

    @Override
    public ProcessDefinition processDefinition(String processDefinitionId) {
        org.activiti.engine.repository.ProcessDefinition processDefinition;
        // try searching by Key if there is no matching by Id
        List<org.activiti.engine.repository.ProcessDefinition> list = repositoryService
                .createProcessDefinitionQuery()
                .processDefinitionKey(processDefinitionId)
                .orderByProcessDefinitionVersion()
                .asc()
                .list();
        if (!list.isEmpty()) {
            processDefinition = list.get(0);
        } else {
            processDefinition = repositoryService.getProcessDefinition(processDefinitionId);
        }
        if (!securityPoliciesManager.canRead(processDefinition.getKey())) {
            throw new ActivitiObjectNotFoundException("Unable to find process definition for the given id:'" + processDefinitionId + "'");
        }
        return processDefinitionConverter.from(processDefinition);
    }

    @Override
    public Page<ProcessDefinition> processDefinitions(Pageable pageable) {
        return processDefinitions(pageable,
                                  ProcessPayloadBuilder.processDefinitions().build());
    }

    @Override
    public Page<ProcessDefinition> processDefinitions(Pageable pageable,
                                                      GetProcessDefinitionsPayload getProcessDefinitionsPayload) {
        if (getProcessDefinitionsPayload == null) {
            throw new IllegalStateException("payload cannot be null");
        }
        GetProcessDefinitionsPayload securityKeysInPayload = securityPoliciesManager.restrictProcessDefQuery(SecurityPolicyAccess.READ);
        // If the security policies keys are not empty it means that I will need to use them to filter results,
        //   else ignore and use the user provided ones.
        if (!securityKeysInPayload.getProcessDefinitionKeys().isEmpty()) {
            getProcessDefinitionsPayload.setProcessDefinitionKeys(securityKeysInPayload.getProcessDefinitionKeys());
        }
        ProcessDefinitionQuery processDefinitionQuery = repositoryService
                .createProcessDefinitionQuery();
        if (getProcessDefinitionsPayload.hasDefinitionKeys()) {
            processDefinitionQuery.processDefinitionKeys(getProcessDefinitionsPayload.getProcessDefinitionKeys());
        }
        return new PageImpl<>(processDefinitionConverter.from(processDefinitionQuery.list()),
                              Math.toIntExact(processDefinitionQuery.count()));
    }

    @Override
    public ProcessInstance processInstance(String processInstanceId) {
        org.activiti.engine.runtime.ProcessInstance internalProcessInstance = runtimeService
                .createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (internalProcessInstance == null) {
            throw new NotFoundException("Unable to find process instance for the given id:'" + processInstanceId + "'");
        }
        if (!securityPoliciesManager.canRead(internalProcessInstance.getProcessDefinitionKey())) {
            throw new ActivitiObjectNotFoundException("You cannot read the process instance with Id:'"
                                                              + processInstanceId + "' due to security policies violation");
        }
        return processInstanceConverter.from(internalProcessInstance);
    }

    @Override
    public Page<ProcessInstance> processInstances(Pageable pageable) {
        return processInstances(pageable,
                                ProcessPayloadBuilder.processInstances().build());
    }

    @Override
    public Page<ProcessInstance> processInstances(Pageable pageable,
                                                  GetProcessInstancesPayload getProcessInstancesPayload) {
        if (getProcessInstancesPayload == null) {
            throw new IllegalStateException("payload cannot be null");
        }
        GetProcessInstancesPayload securityKeysInPayload = securityPoliciesManager.restrictProcessInstQuery(SecurityPolicyAccess.READ);

        org.activiti.engine.runtime.ProcessInstanceQuery internalQuery = runtimeService.createProcessInstanceQuery();

        if (!securityKeysInPayload.getProcessDefinitionKeys().isEmpty()) {
            getProcessInstancesPayload.setProcessDefinitionKeys(securityKeysInPayload.getProcessDefinitionKeys());
        }
        if (getProcessInstancesPayload.getProcessDefinitionKeys() != null &&
                !getProcessInstancesPayload.getProcessDefinitionKeys().isEmpty()) {
            internalQuery.processDefinitionKeys(getProcessInstancesPayload.getProcessDefinitionKeys());
        }
        if (getProcessInstancesPayload.getBusinessKey() != null &&
                !getProcessInstancesPayload.getBusinessKey().isEmpty()) {
            internalQuery.processInstanceBusinessKey(getProcessInstancesPayload.getBusinessKey());
        }

        if (getProcessInstancesPayload.isSuspendedOnly()) {
            internalQuery.suspended();
        }

        if (getProcessInstancesPayload.isActiveOnly()) {
            internalQuery.active();
        }

        return new PageImpl<>(processInstanceConverter.from(internalQuery.listPage(pageable.getStartIndex(),
                                                                                   pageable.getMaxItems())),
                              Math.toIntExact(internalQuery.count()));
    }

    @Override
    public ProcessRuntimeConfiguration configuration() {
        return configuration;
    }

    @Override
    public ProcessInstance start(StartProcessPayload startProcessPayload) {
        ProcessDefinition processDefinition = null;
        if (startProcessPayload.getProcessDefinitionId() != null) {
            processDefinition = processDefinition(startProcessPayload.getProcessDefinitionId());
        }
        if (processDefinition == null && startProcessPayload.getProcessDefinitionKey() != null) {
            processDefinition = processDefinition(startProcessPayload.getProcessDefinitionKey());
        }
        if (processDefinition == null) {
            throw new IllegalStateException("At least Process Definition Id or Key needs to be provided to start a process");
        }
        if (!securityPoliciesManager.canWrite(processDefinition.getKey())) {
            throw new ActivitiForbiddenException("Operation not permitted for " + processDefinition.getKey() + " due security policy violation");
        }
        return processInstanceConverter.from(runtimeService
                                                     .createProcessInstanceBuilder()
                                                     .processDefinitionId(startProcessPayload.getProcessDefinitionId())
                                                     .processDefinitionKey(startProcessPayload.getProcessDefinitionKey())
                                                     .businessKey(startProcessPayload.getBusinessKey())
                                                     .variables(startProcessPayload.getVariables())
                                                     .name(startProcessPayload.getProcessInstanceName())
                                                     .start());
    }

    @Override
    public ProcessInstance suspend(SuspendProcessPayload suspendProcessPayload) {
        ProcessInstance processInstance = processInstance(suspendProcessPayload.getProcessInstanceId());
        if (!securityPoliciesManager.canWrite(processInstance.getProcessDefinitionKey())) {
            throw new ActivitiForbiddenException("Operation not permitted for " + processInstance.getProcessDefinitionKey() + " due security policy violation");
        }
        runtimeService.suspendProcessInstanceById(suspendProcessPayload.getProcessInstanceId());
        return processInstanceConverter.from(runtimeService.createProcessInstanceQuery().processInstanceId(suspendProcessPayload.getProcessInstanceId()).singleResult());
    }

    @Override
    public ProcessInstance resume(ResumeProcessPayload resumeProcessPayload) {
        ProcessInstance processInstance = processInstance(resumeProcessPayload.getProcessInstanceId());
        if (!securityPoliciesManager.canWrite(processInstance.getProcessDefinitionKey())) {
            throw new ActivitiForbiddenException("Operation not permitted for " + processInstance.getProcessDefinitionKey() + " due security policy violation");
        }
        runtimeService.activateProcessInstanceById(resumeProcessPayload.getProcessInstanceId());
        return processInstanceConverter.from(runtimeService.createProcessInstanceQuery()
                                                     .processInstanceId(resumeProcessPayload.getProcessInstanceId()).singleResult());
    }

    @Override
    public ProcessInstance delete(DeleteProcessPayload deleteProcessPayload) {
        ProcessInstanceImpl processInstance = (ProcessInstanceImpl) processInstance(deleteProcessPayload.getProcessInstanceId());
        if (!securityPoliciesManager.canWrite(processInstance.getProcessDefinitionKey())) {
            throw new ActivitiForbiddenException("Operation not permitted for " + processInstance.getProcessDefinitionKey() + " due security policy violation");
        }
        runtimeService.deleteProcessInstance(deleteProcessPayload.getProcessInstanceId(),
                                             deleteProcessPayload.getReason());
        processInstance.setStatus(ProcessInstance.ProcessInstanceStatus.DELETED);
        return processInstance;
    }

    @Override
    public List<VariableInstance> variables(GetVariablesPayload getVariablesPayload) {
        //Process Instance will check security policies on read
        processInstance(getVariablesPayload.getProcessInstanceId());

        Map<String, org.activiti.engine.impl.persistence.entity.VariableInstance> variables;
        variables = runtimeService.getVariableInstances(getVariablesPayload.getProcessInstanceId());

        return variableInstanceConverter.from(variables.values());
    }

    @Override
    public void removeVariables(RemoveProcessVariablesPayload removeProcessVariablesPayload) {
        ProcessInstanceImpl processInstance = (ProcessInstanceImpl) processInstance(removeProcessVariablesPayload.getProcessInstanceId());
        if (!securityPoliciesManager.canWrite(processInstance.getProcessDefinitionKey())) {
            throw new ActivitiForbiddenException("Operation not permitted for " + processInstance.getProcessDefinitionKey() + " due security policy violation");
        }
        runtimeService.removeVariables(removeProcessVariablesPayload.getProcessInstanceId(),
                                           removeProcessVariablesPayload.getVariableNames());

    }

    @Override
    public void setVariables(SetProcessVariablesPayload setProcessVariablesPayload) {
        ProcessInstanceImpl processInstance = (ProcessInstanceImpl) processInstance(setProcessVariablesPayload.getProcessInstanceId());
        if (!securityPoliciesManager.canWrite(processInstance.getProcessDefinitionKey())) {
            throw new ActivitiForbiddenException("Operation not permitted for " + processInstance.getProcessDefinitionKey() + " due security policy violation");
        }
        runtimeService.setVariables(setProcessVariablesPayload.getProcessInstanceId(),
                                        setProcessVariablesPayload.getVariables());
        
    }

    @Override
    public void signal(SignalPayload signalPayload) {
        //@TODO: define security policies for signalling
        runtimeService.signalEventReceived(signalPayload.getName(),
                                           signalPayload.getVariables());
    }

    @Override
    public ProcessDefinitionMeta processDefinitionMeta(String processDefinitionKey) {
        //Process Definition will check security policies on read
        processDefinition(processDefinitionKey);
        return new ProcessDefinitionMetaImpl(processDefinitionKey);
    }

    @Override
    public ProcessInstanceMeta processInstanceMeta(String processInstanceId) {
        //Process Instance will check security policies on read
        processInstance(processInstanceId);
        ProcessInstanceMetaImpl processInstanceMeta = new ProcessInstanceMetaImpl(processInstanceId);
        processInstanceMeta.setActiveActivitiesIds(runtimeService.getActiveActivityIds(processInstanceId));
        return processInstanceMeta;
    }

    @Override
    public ProcessInstance update(UpdateProcessPayload updateProcessPayload) {
        return null;
    }
}
