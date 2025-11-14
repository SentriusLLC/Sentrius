# Automation Suggestions UI

This document describes the UI components added for visualizing and managing automation suggestions.

## Navigation

A new menu item has been added to the sidebar navigation:
- **Icon**: Magic wand (fa-magic)
- **Label**: "Automation Suggestions"
- **Access Control**: Requires `CAN_MANAGE_SYSTEMS` permission
- **URL**: `/sso/v1/automation/suggestions/list`

## Main Dashboard (`suggestions_list.html`)

### Overview Statistics Card
Displays real-time statistics in a 4-column layout:
- **Pending Review**: Count of suggestions awaiting approval
- **Approved**: Count of approved suggestions
- **High Confidence**: Count of suggestions with ≥70% confidence
- **Converted**: Count of suggestions converted to automations

### Suggestions Table
DataTable with the following columns:
1. **ID**: Suggestion identifier
2. **Description**: AI-generated description of the automation
3. **Type**: Badge showing script type (Bash/Python/PowerShell)
4. **Target**: Target system hostname
5. **Confidence**: Color-coded badge (Green ≥70%, Yellow ≥50%, Red <50%)
6. **Frequency**: Number of sessions showing the pattern
7. **Status**: Badge showing PENDING/APPROVED/REJECTED/CONVERTED
8. **Created**: Creation date
9. **Actions**: Context-aware action buttons

### Action Buttons
**For PENDING suggestions:**
- 👁️ View (Blue) - Shows full details in modal
- ✓ Approve (Green) - Opens approval modal
- ✗ Reject (Red) - Opens rejection modal
- 🗑️ Delete (Gray) - Deletes the suggestion

**For APPROVED suggestions:**
- 👁️ View
- ⚙️ Convert (Blue) - Converts to executable automation
- 🗑️ Delete

### Modals

#### View Details Modal
Shows comprehensive suggestion information:
- All metadata fields
- Complete script preview with syntax highlighting
- Session IDs that contributed to the pattern
- Confidence score and pattern frequency

#### Approve Modal
- Optional comments field
- Approve button (green)
- Cancel button

#### Reject Modal
- Optional rejection reason field
- Reject button (red)
- Cancel button

## REST API Endpoints

### GET `/api/v1/automation/suggestions/all`
Returns all suggestions with full details

### GET `/api/v1/automation/suggestions/pending`
Returns only pending suggestions

### GET `/api/v1/automation/suggestions/{id}`
Returns specific suggestion details

### POST `/api/v1/automation/suggestions/{id}/approve`
Approves a suggestion
```json
{
  "comments": "Optional approval comments",
  "modifiedScript": "Optional modified script"
}
```

### POST `/api/v1/automation/suggestions/{id}/reject`
Rejects a suggestion
```json
{
  "comments": "Optional rejection reason"
}
```

### POST `/api/v1/automation/suggestions/{id}/convert`
Converts approved suggestion to executable automation

### DELETE `/api/v1/automation/suggestions/{id}`
Deletes a suggestion

## Visual Design

### Color Scheme
- Dark theme consistent with existing Sentrius UI
- Background: `#2b3e50` (card background)
- Borders: `#405d80`
- Text: White (`#fff`)

### Confidence Badges
- **High (≥70%)**: Green background (`#28a745`)
- **Medium (≥50%)**: Yellow background (`#ffc107`) with black text
- **Low (<50%)**: Red background (`#dc3545`)

### Status Badges
- **PENDING**: Teal (`#17a2b8`)
- **APPROVED**: Green (`#28a745`)
- **REJECTED**: Red (`#dc3545`)
- **CONVERTED**: Gray (`#6c757d`)

### Script Type Badges
- **Bash**: Gray secondary badge
- **Python**: Blue primary badge
- **PowerShell**: Teal info badge

## User Workflow

1. **Navigate** to Automation Suggestions from sidebar
2. **Review** statistics to see pending work
3. **View** suggestion details by clicking eye icon
4. **Approve or Reject** based on business needs
   - Can add comments during approval/rejection
   - Can modify script before approval
5. **Convert** approved suggestions to automations
6. **Monitor** converted automations through existing automation UI

## Integration Points

- **Session Analysis**: Analyzer runs every 6 hours, populating suggestions
- **Automation Conversion**: Creates entries in `automation` table
- **Access Control**: Uses existing ABAC/SSHAccessEnum permissions
- **Navigation**: Integrated into main sidebar menu
- **Styling**: Consistent with existing Thymeleaf templates

## Screenshots

### Main Dashboard
```
┌─────────────────────────────────────────────────────────────────┐
│ 🪄 Automation Suggestions                                      │
│ AI-generated automation suggestions based on repetitive...      │
├─────────────────────────────────────────────────────────────────┤
│ ┌─────────────────── Suggestion Statistics ─────────────────┐ │
│ │   [0]          [0]           [0]              [0]         │ │
│ │  Pending    Approved    High Confidence    Converted      │ │
│ └───────────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────────┤
│ ┌──────────────────── All Suggestions ─────────────────────┐ │
│ │ ID │ Description │ Type │ Target │ Conf │ Freq │ Status │ │
│ │ -- │ ----------- │ ---- │ ------ │ ---- │ ---- │ ------ │ │
│ │ 1  │ Deploy...   │ Bash │ web1   │ 85%  │ 5    │ PENDING│ │
│ │ 2  │ Backup...   │ Py   │ db1    │ 72%  │ 3    │ PENDING│ │
│ └───────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

This UI provides a complete "single pane of glass" for managing automation suggestions, allowing administrators to review, approve, and deploy AI-generated automations through an intuitive web interface.
