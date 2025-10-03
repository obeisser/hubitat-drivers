AI PROMPT TITLE: Hubitat Miele@Home Integration Suite (Parent App & Child Drivers)


OBJECTIVE: Generate a production-ready Hubitat Groovy SmartApp (Parent) and two Child Drivers (Washer & Oven).


ENVIRONMENT & CONSTRAINTS:
- Language: Hubitat Groovy 2.4/3.0
- Use asynchronous Hubitat HTTP methods only (asynchttpGet/Post/Put).
- Parent App performs OAuth Authorization Code flow and manages tokens (access + refresh) in `state`.
- Parent maintains a single SSE connection to `/v1/event`, processes incremental JSON chunks with JsonSlurper(type: LAX), and calls `childDevice.receiveMieleState(stateMap)`.
- Child drivers implement `receiveMieleState(Map stateData)` and proxy commands via `parent.childDriverCommand(deviceNetworkId, commandName, params)`.


MIELE API ENDPOINTS:
- Auth: https://auth.domestic.miele-iot.com/partner/realms/mcs/protocol/openid-connect/auth
- Token: https://auth.domestic.miele-iot.com/partner/realms/mcs/protocol/openid-connect/token
- Devices: /v1/devices
- Event: /v1/event
- Actions: /v1/devices/{deviceId}/actions (PUT)


DELIVERABLES:
- SmartApp skeleton with oauth:true, preferences for clientId/secret, oauthCallback, tokenHandler, tokenRefreshJob, discoverDevices(), startEventStream(), eventStreamHandler(), childDriverCommand().
- Two Child Driver templates: Washer (Switch/Refresh/PowerMeter + attributes) and Oven (Switch/Light/Climate + attributes).
- Use typed variables, robust error handling and backoff logic for SSE reconnections.

REPOSITORY LAYOUT
- Parent APP: /APP
- Child Drivers: /Drivers