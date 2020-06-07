SELECT
  app_uuid, artifact_qualified_name, create_date, last_updated, endpoint, subscribe_automatically,
  force_subscribe, module_name, component, endpoint_name, endpoint_ids, active_failing, latest_failed_service_instance
FROM source_artifact
WHERE 1=1
AND app_uuid = ?
AND (subscribe_automatically = TRUE OR force_subscribe = TRUE);