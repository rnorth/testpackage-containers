# Injected by testcontainers
# This file contains cluster properties which will only be considered when
# starting the cluster for the first time. Afterwards, you can configure cluster
# properties via the Redpanda Admin API.
superusers:
<#if kafkaApi.superusers?has_content >
    <#list kafkaApi.superusers as superuser>
  - ${superuser}
    </#list>
<#else>
  []
</#if>

<#if kafkaApi.enableAuthorization >
kafka_enable_authorization: true
</#if>

auto_create_topics_enabled: true
