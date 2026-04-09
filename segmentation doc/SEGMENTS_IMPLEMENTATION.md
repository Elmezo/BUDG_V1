# Segments Implementation Guide

## Overview
This document describes the implementation of the Segments feature in the BUDG Admin Panel, based on Informatica Axon Data Governance 7.0+ functionality. Segments allow Super Admin users to restrict user access to limited objects in Axon based on location, business area, and role in an organization.

## Features

### 1. Segment Management
- **List View**: Display all segments with name and description
- **Create Segment**: Only Super Admin users can create segments
- **Edit Segment**: Modify segment details and assignments
- **Delete Segment**: Remove segments (with appropriate validation)

### 2. Segment Configuration
Each segment includes:
- **Name**: Required field for segment identification
- **Description**: Optional description of the segment's purpose
- **Segment Admin Users**: Users with Admin profile who can manage the segment
- **Assigned Users**: Users who can access the segment content
  - Assigned by Org Units: All users in selected organizational units
  - Assigned Manually: Individual users selected manually
  - Assigned via SSO: Users assigned through Single Sign-On

## User Interface

### List View
- **Location**: Admin Panel → Meta-Model Administration → Segments
- **Features**:
  - Table displaying all segments (Name, Description)
  - "Create" button in top-left corner
  - Click on segment name to view/edit details

### Detail View
The detail view has two tabs:

#### SUMMARY Tab
- **DEFINITION Section**:
  - Name* (required text field)
  - Description* (required textarea)
  - Show Editor link (for rich text editing)
  
- **SEGMENT ADMIN USERS Section**:
  - Two sub-tabs: "Assigned Manually" and "Assigned via SSO"
  - "+ Add" button to open modal for selecting Segment Admin Users
  - Table showing assigned Segment Admin Users
  - Delete button (enabled when items are selected)

#### ASSIGNED USERS Tab
- **Three assignment methods**:
  1. **Assigned by Org Units**: 
     - "+ Add" button opens modal to select organizational units
     - All users in selected org units can access the segment
  2. **Assigned Manually**:
     - "+ Add" button opens modal to select individual users
     - Selected users can access the segment
  3. **Assigned via SSO**:
     - SSO-based user assignment (future implementation)

- Each section has:
  - Table showing assigned entities (Name, Description, Parent for org units)
  - "+ Add" and "Delete" buttons
  - Record count display

## Modal Dialogs

### Select Segment Admin Users Modal
**Triggered by**: Clicking "+ Add" in Segment Admin Users section

**Features**:
- Filter fields:
  - Name (text input)
  - Org Unit (text input)
  - Profile (text input)
  - Function (text input)
  - Email (text input)
- User list table with columns:
  - User name (with person icon)
  - Org Unit (with org structure icon)
  - Profile
  - Function
  - Email
- Action buttons:
  - "Select" (primary button)
  - "Close" (secondary button)
- Help icon (question mark)

### Select Org Units Modal
**Triggered by**: Clicking "+ Add" in Assigned by Org Units section

**Features**:
- Filter fields below each column header:
  - Org Unit (text input)
  - Description (text input)
  - Parent (text input)
- Org Units table with columns:
  - Org Unit (with person icon)
  - Description
  - Parent (with org structure icon)
- Action buttons:
  - "Select" (primary button)
  - "Close" (secondary button)
- Help icon (question mark)

### Select Users Modal
**Triggered by**: Clicking "+ Add" in Assigned Manually section

**Features**:
- Similar to Segment Admin Users modal
- Filter fields for searching users
- User selection table
- Select and Close buttons

## Color Scheme
The implementation uses the project's standard color palette:
- **Primary Color**: `#248567` (teal/green) - buttons, active states, links
- **Secondary Colors**: `#1b857a`, `#1f6f4e` (darker teal) - hover states
- **Background**: `#ffffff` (white) and `#f8f9fa` (light grey)
- **Text**: `#2c3e50` (dark grey), `#6c757d` (medium grey)
- **Borders**: `#e9ecef` (light grey)
- **Accent**: `#48758c` (blue-grey) for icons and secondary elements

## File Structure

### Frontend Files
1. **`src/main/webapp/assets/js/admin-panel/meta-model/segments.js`**
   - Main JavaScript file containing all segment management logic
   - Handles list view, detail view, modals, and API interactions

2. **`src/main/webapp/assets/css/admin-panel/segments.css`**
   - Styling for segments feature
   - Matches project color scheme and design patterns

3. **`src/main/webapp/admin-panel.html`**
   - Updated to include segments.css link

### Backend Requirements (To Be Implemented)
The following API endpoints should be created:

1. **GET `/api/segments`**
   - Returns list of all segments

2. **GET `/api/segments/{id}`**
   - Returns segment details by ID

3. **POST `/api/segments`**
   - Creates a new segment
   - Requires: name, description
   - Returns: created segment with ID

4. **PUT `/api/segments/{id}`**
   - Updates segment details
   - Requires: name, description

5. **DELETE `/api/segments/{id}`**
   - Deletes a segment
   - Validates no dependencies before deletion

6. **GET `/api/segments/{id}/admin-users`**
   - Returns Segment Admin Users for a segment

7. **POST `/api/segments/{id}/admin-users`**
   - Assigns Segment Admin Users to a segment

8. **DELETE `/api/segments/{id}/admin-users/{userId}`**
   - Removes a Segment Admin User from a segment

9. **GET `/api/segments/{id}/assigned-org-units`**
   - Returns assigned organizational units

10. **POST `/api/segments/{id}/assigned-org-units`**
    - Assigns organizational units to a segment

11. **DELETE `/api/segments/{id}/assigned-org-units/{orgUnitId}`**
    - Removes an organizational unit from a segment

12. **GET `/api/segments/{id}/assigned-users`**
    - Returns manually assigned users

13. **POST `/api/segments/{id}/assigned-users`**
    - Assigns users manually to a segment

14. **DELETE `/api/segments/{id}/assigned-users/{userId}`**
    - Removes a user from a segment

## Database Schema (Suggested)

```sql
-- Segments table
CREATE TABLE segments (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by INT,
    updated_by INT
);

-- Segment Admin Users (many-to-many)
CREATE TABLE segment_admin_users (
    segment_id INT NOT NULL,
    user_id INT NOT NULL,
    assignment_type ENUM('manual', 'sso') DEFAULT 'manual',
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (segment_id, user_id),
    FOREIGN KEY (segment_id) REFERENCES segments(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES people(ID) ON DELETE CASCADE
);

-- Segment Org Units (many-to-many)
CREATE TABLE segment_org_units (
    segment_id INT NOT NULL,
    org_unit_id INT NOT NULL,
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (segment_id, org_unit_id),
    FOREIGN KEY (segment_id) REFERENCES segments(id) ON DELETE CASCADE,
    FOREIGN KEY (org_unit_id) REFERENCES org_unit(ID) ON DELETE CASCADE
);

-- Segment Users (many-to-many for manual assignment)
CREATE TABLE segment_users (
    segment_id INT NOT NULL,
    user_id INT NOT NULL,
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (segment_id, user_id),
    FOREIGN KEY (segment_id) REFERENCES segments(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES people(ID) ON DELETE CASCADE
);
```

## Usage Flow

### Creating a Segment
1. Navigate to Admin Panel → Meta-Model Administration → Segments
2. Click "Create" button (top-left)
3. Enter Name and Description in SUMMARY tab
4. Click "+ Add" in Segment Admin Users section
5. Select users with Admin profile from modal
6. Click "Select" to confirm
7. Go to ASSIGNED USERS tab
8. Click "+ Add" in "Assigned by Org Units" section
9. Select organizational units from modal
10. Optionally add individual users in "Assigned Manually" section
11. Click "Save" or "Save & Close"

### Editing a Segment
1. Click on segment name in list view
2. Modify fields as needed
3. Add/remove Segment Admin Users, Org Units, or Users
4. Click "Save" or "Save & Close"

## Access Control
- **Create Segments**: Only Super Admin users
- **Edit Segments**: Super Admin and Segment Admin Users
- **View Segments**: All authenticated users (with filtered content based on assignments)

## Implementation Notes
- All modals use the project's standard modal styling
- Tables follow the same pattern as dropdown-configs and custom-fields
- Filter functionality in modals uses real-time search
- Validation ensures required fields are filled before saving
- Error handling displays user-friendly messages
- Loading states shown during API calls

## Future Enhancements
- SSO-based user assignment
- Bulk assignment operations
- Segment templates
- Advanced filtering and search
- Export/import functionality
- Audit logging for segment changes

