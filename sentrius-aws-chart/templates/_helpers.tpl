{{- define "sentrius.labels" -}}
app.kubernetes.io/name: sentrius
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}
