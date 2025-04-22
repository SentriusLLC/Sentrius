{{- define "sentrius.labels" -}}
app.kubernetes.io/name: sentrius
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}
{{- define "sentriusagent.labels" -}}
app.kubernetes.io/name: sentrius-agent
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}
{{- define "sentriusaiagent.labels" -}}
app.kubernetes.io/name: sentrius-ai-agent
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}
