SELECT app_uuid, app_name, create_date, agent_config
FROM source_application
WHERE app_uuid = ?;