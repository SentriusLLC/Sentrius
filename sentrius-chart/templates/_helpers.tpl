{{- define "sentrius.labels" -}}
app.kubernetes.io/name: sentrius
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{- define "sentrius.fullname" -}}
{{- if .Release.Name }}
{{- .Release.Name }}
{{- else }}
sentrius
{{- end }}
{{- end -}}

{{- define "sentrius.selectorLabels" -}}
app.kubernetes.io/name: sentrius
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "sentriusagent.labels" -}}
app.kubernetes.io/name: sentrius-agent
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}
{{- define "monitoring-agent.labels" -}}
app.kubernetes.io/name: sentrius-monitoring-agent
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}
{{- define "launcherservice.labels" -}}
app.kubernetes.io/name: sentrius-launcher-service
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}
{{- define "keycloak.requireDbPassword" -}}
{{- if not .Values.keycloak.db.password }}
  {{- fail "Error: keycloak.db.password must be specified or generated externally." }}
{{- end }}
{{- end }}