/**
 * Entity Field Configuration
 * Defines field metadata for each entity type and operation
 * Expandable architecture: Add new entities by creating similar structures
 */

// Import relationship field configurations
import { RELATIONSHIP_FIELD_MAP } from './relationshipFields.js';

export const REGULATOR_FIELDS = {
  INSERT: [
    {
      name: 'PrimaryName',
      displayName: 'Primary Name',
      required: true,
      dataType: 'STRING',
      description: 'Primary name of the regulator (required)'
    },
    {
      name: 'ShortName',
      displayName: 'Short Name',
      required: false,
      dataType: 'STRING',
      description: 'Short name or abbreviation of the regulator'
    },
    {
      name: 'Description',
      displayName: 'Description',
      required: false,
      dataType: 'STRING',
      description: 'Detailed description of the regulator'
    }
  ],
  UPDATE: [
    {
      name: 'Regulator ID',
      displayName: 'Regulator ID',
      required: true,
      dataType: 'INTEGER',
      description: 'Regulator unique identifier (required for updates)'
    },
    {
      name: 'Regulator Name',
      displayName: 'Regulator Name',
      required: true,
      dataType: 'STRING',
      description: 'Primary name of the regulator (required)'
    },
    {
      name: 'Short Name',
      displayName: 'Short Name',
      required: true,
      dataType: 'STRING',
      description: 'Short name or abbreviation of the regulator (required)'
    },
    {
      name: 'Description',
      displayName: 'Description',
      required: false,
      dataType: 'STRING',
      description: 'Detailed description of the regulator'
    }
  ],
  DELETE: [
    {
      name: 'ID',
      displayName: 'ID',
      required: true,
      dataType: 'INTEGER',
      description: 'Regulator unique identifier to delete (required)'
    }
  ]
};

export const GEOGRAPHY_FIELDS = {
  INSERT: [
    {
      name: 'Geography Name',
      displayName: 'Geography Name',
      required: true,
      dataType: 'STRING',
      description: 'Primary name of the geography (required, must be unique)'
    },
    {
      name: 'Geography Definition',
      displayName: 'Geography Definition',
      required: false,
      dataType: 'STRING',
      description: 'Detailed description or definition of the geography'
    },
    {
      name: 'Parent Geography Name',
      displayName: 'Parent Geography Name',
      required: false,
      dataType: 'STRING',
      description: 'Name of the parent geography (must exist in database)'
    }
  ],
  UPDATE: [
    {
      name: 'Geography ID',
      displayName: 'Geography ID',
      required: true,
      dataType: 'INTEGER',
      description: 'Geography unique identifier (required for updates)'
    },
    {
      name: 'Geography Name',
      displayName: 'Geography Name',
      required: true,
      dataType: 'STRING',
      description: 'Primary name of the geography (required, must be unique)'
    },
    {
      name: 'Geography Definition',
      displayName: 'Geography Definition',
      required: false,
      dataType: 'STRING',
      description: 'Detailed description or definition of the geography'
    },
    {
      name: 'Parent Geography Name',
      displayName: 'Parent Geography Name',
      required: false,
      dataType: 'STRING',
      description: 'Name of the parent geography (must exist in database)'
    }
  ],
  DELETE: [
    {
      name: 'Geography ID',
      displayName: 'Geography ID',
      required: true,
      dataType: 'INTEGER',
      description: 'Geography unique identifier to delete (required)'
    }
  ]
};

export const REGULATORY_THEME_FIELDS = {
  INSERT: [
    {
      name: 'Regulatory Theme Long Name',
      displayName: 'Regulatory Theme Long Name',
      required: false,
      dataType: 'STRING',
      description: 'Primary name of the regulatory theme (must be unique if provided)'
    },
    {
      name: 'Reference',
      displayName: 'Reference',
      required: false,
      dataType: 'STRING',
      description: 'Reference number for the regulatory theme'
    },
    {
      name: 'Short Name',
      displayName: 'Short Name',
      required: false,
      dataType: 'STRING',
      description: 'Short name or abbreviation of the regulatory theme'
    },
    {
      name: 'Description',
      displayName: 'Description',
      required: false,
      dataType: 'STRING',
      description: 'Detailed description of the regulatory theme'
    },
    {
      name: 'Parent Regulatory Theme Name',
      displayName: 'Parent Regulatory Theme Name',
      required: false,
      dataType: 'STRING',
      description: 'Name of the parent regulatory theme (must exist in database)'
    },
    {
      name: 'Parent Ref.',
      displayName: 'Parent Ref.',
      required: false,
      dataType: 'STRING',
      description: 'Reference number of the parent regulatory theme (alternative to name)'
    },
    {
      name: 'BUDG Status',
      displayName: 'BUDG Status',
      required: false,
      dataType: 'STRING',
      description: 'Status of the regulatory theme (Active, Inactive, Pending Review, Obsolete, Deleted)',
      aliases: ['Axon Status', 'Status', 'status', 'Object Status']
    }
  ],
  UPDATE: [
    {
      name: 'Regulatory Theme ID',
      displayName: 'Regulatory Theme ID',
      required: false,
      dataType: 'INTEGER',
      description: 'Regulatory Theme unique identifier (for updates)'
    },
    {
      name: 'Reference',
      displayName: 'Reference',
      required: false,
      dataType: 'STRING',
      description: 'Reference number for the regulatory theme'
    },
    {
      name: 'Short Name',
      displayName: 'Short Name',
      required: false,
      dataType: 'STRING',
      description: 'If provided, this value becomes the new Short Name for the specified object.'
    },
    {
      name: 'Regulatory Theme Long Name',
      displayName: 'Regulatory Theme Long Name',
      required: false,
      dataType: 'STRING',
      description: 'Primary name of the regulatory theme (must be unique if provided)'
    },
    {
      name: 'Description',
      displayName: 'Description',
      required: false,
      dataType: 'STRING',
      description: 'Detailed description of the regulatory theme'
    },
    {
      name: 'Parent Regulatory Theme Name',
      displayName: 'Parent Regulatory Theme Name',
      required: false,
      dataType: 'STRING',
      description: 'Name of the parent regulatory theme (must exist in database)'
    },
    {
      name: 'Parent Ref.',
      displayName: 'Parent Ref.',
      required: false,
      dataType: 'STRING',
      description: 'Reference number of the parent regulatory theme (alternative to name)'
    },
    {
      name: 'BUDG Status',
      displayName: 'BUDG Status',
      required: false,
      dataType: 'LIST',
      description: 'Status of the regulatory theme (dropdown from status table)',
      aliases: ['Axon Status', 'Status', 'status', 'Object Status']
    }
  ],
  DELETE: [
    {
      name: 'Regulatory Theme ID',
      displayName: 'Regulatory Theme ID',
      required: false,
      dataType: 'INTEGER',
      description: 'Regulatory Theme unique identifier to delete'
    }
  ]
};

/**
 * Get field configuration based on entity and operation
 * @param {string} entity - Entity name (e.g., 'Regulator')
 * @param {string} operation - Operation type (INSERT, UPDATE, DELETE)
 * @returns {Array} Field definitions
 */
export const REGULATION_FIELDS = {
  INSERT: [
    {
      name: 'Reference',
      displayName: 'Reference',
      required: false,
      dataType: 'STRING',
      description: 'Reference number for the regulation (auto-generated if empty)'
    },
    {
      name: 'Short Name',
      displayName: 'Short Name',
      required: false,
      dataType: 'STRING',
      description: 'Short name or abbreviation of the regulation'
    },
    {
      name: 'Regulation Long Name',
      displayName: 'Regulation Long Name',
      required: true,
      dataType: 'STRING',
      description: 'Primary name of the regulation (required, must be unique)'
    },
    {
      name: 'Description',
      displayName: 'Description',
      required: true,
      dataType: 'STRING',
      description: 'Detailed description of the regulation (required)'
    },
    {
      name: 'Additional Info',
      displayName: 'Additional Info',
      required: false,
      dataType: 'STRING',
      description: 'Additional information about the regulation'
    },
    {
      name: 'Publication Date',
      displayName: 'Publication Date',
      required: false,
      dataType: 'DATE',
      description: 'Publication date (format: 25th May 2018 or YYYY-MM-DD)'
    },
    {
      name: 'Comments Date',
      displayName: 'Comments Date',
      required: false,
      dataType: 'DATE',
      description: 'Comments date (format: 25th May 2018 or YYYY-MM-DD)'
    },
    {
      name: 'Finalisation Date',
      displayName: 'Finalisation Date',
      required: false,
      dataType: 'DATE',
      description: 'Finalisation date (format: 25th May 2018 or YYYY-MM-DD)'
    },
    {
      name: 'Compliance Date',
      displayName: 'Compliance Date',
      required: false,
      dataType: 'DATE',
      description: 'Compliance date (format: 25th May 2018 or YYYY-MM-DD)'
    },
    {
      name: 'Legal Advice',
      displayName: 'Legal Advice',
      required: false,
      dataType: 'STRING',
      description: 'Legal advice text'
    },
    {
      name: 'Parent Regulation Name',
      displayName: 'Parent Regulation Name',
      required: false,
      dataType: 'STRING',
      description: 'Name of the parent regulation (must exist in database)'
    },
    {
      name: 'Parent Ref.',
      displayName: 'Parent Ref.',
      required: false,
      dataType: 'STRING',
      description: 'Reference number of the parent regulation (alternative to name)'
    },
    {
      name: 'Regulation Maturity',
      displayName: 'Regulation Maturity',
      required: false,
      dataType: 'STRING',
      description: 'Regulation maturity status (Initial Draft, In Effect, Obsolete)'
    },
    {
      name: 'Regulation Probability',
      displayName: 'Regulation Probability',
      required: false,
      dataType: 'STRING',
      description: 'Regulation probability (Confirmed)'
    },
    {
      name: 'BUDG Status',
      displayName: 'BUDG Status',
      required: false,
      dataType: 'STRING',
      description: 'BUDG Status (Active, Obsolete, Deleted) - defaults to 1 if empty',
      aliases: ['Axon Status', 'Status', 'status', 'Object Status']
    },
    {
      name: 'Legal Advice Type',
      displayName: 'Legal Advice Type',
      required: false,
      dataType: 'STRING',
      description: 'Legal advice type (Comment, Guideline, Legal Requirement)'
    },
    {
      name: 'Regulation Stage',
      displayName: 'Regulation Stage',
      required: false,
      dataType: 'STRING',
      description: 'Regulation stage (Interpretation, Impact Assessment, Completed)'
    },
    {
      name: 'Compliance Level',
      displayName: 'Compliance Level',
      required: false,
      dataType: 'STRING',
      description: 'Compliance level (Unknown, Fully Compliant, Compliant with exceptions, Materially Not Compliant)'
    },
    {
      name: 'User Email',
      displayName: 'User Email',
      required: false,
      dataType: 'STRING',
      description: 'Stakeholder user email'
    },
    {
      name: 'User First Name',
      displayName: 'User First Name',
      required: false,
      dataType: 'STRING',
      description: 'Stakeholder first name'
    },
    {
      name: 'User Last Name',
      displayName: 'User Last Name',
      required: false,
      dataType: 'STRING',
      description: 'Stakeholder last name'
    },
    {
      name: 'User Lan ID',
      displayName: 'User Lan ID',
      required: false,
      dataType: 'STRING',
      description: 'Stakeholder LAN ID'
    },
    {
      name: 'Governance Role',
      displayName: 'Governance Role',
      required: false,
      dataType: 'STRING',
      description: 'Governance role (Regulation Owner, Regulation SME)'
    }
  ],
  UPDATE: [
    {
      name: 'Regulation ID',
      displayName: 'Regulation ID',
      required: true,
      dataType: 'INTEGER',
      description: 'Regulation unique identifier (required for updates)'
    },
    {
      name: 'Regulation Long Name',
      displayName: 'Regulation Long Name',
      required: true,
      dataType: 'STRING',
      description: 'Primary name of the regulation (required, must be unique)'
    },
    {
      name: 'Description',
      displayName: 'Description',
      required: true,
      dataType: 'STRING',
      description: 'Detailed description of the regulation (required)'
    },
    {
      name: 'Publication Date',
      displayName: 'Publication Date',
      required: true,
      dataType: 'DATE',
      description: 'Publication date (required, format: 25th May 2018 or YYYY-MM-DD)'
    },
    {
      name: 'Compliance Date',
      displayName: 'Compliance Date',
      required: true,
      dataType: 'DATE',
      description: 'Compliance date (required, format: 25th May 2018 or YYYY-MM-DD)'
    },
    {
      name: 'Regulation Maturity',
      displayName: 'Regulation Maturity',
      required: true,
      dataType: 'STRING',
      description: 'Regulation maturity status (required: Initial Draft, In Effect, Obsolete)'
    },
    {
      name: 'Regulation Probability',
      displayName: 'Regulation Probability',
      required: true,
      dataType: 'STRING',
      description: 'Regulation probability (required: Confirmed)'
    },
    {
      name: 'Regulation Stage',
      displayName: 'Regulation Stage',
      required: true,
      dataType: 'STRING',
      description: 'Regulation stage (required: Interpretation, Impact Assessment, Completed)'
    },
    {
      name: 'Compliance Level',
      displayName: 'Compliance Level',
      required: true,
      dataType: 'STRING',
      description: 'Compliance level (required: Unknown, Fully Compliant, Compliant with exceptions, Materially Not Compliant)'
    },
    {
      name: 'Reference',
      displayName: 'Reference',
      required: false,
      dataType: 'STRING',
      description: 'Reference number for the regulation'
    },
    {
      name: 'Short Name',
      displayName: 'Short Name',
      required: false,
      dataType: 'STRING',
      description: 'Short name or abbreviation of the regulation'
    },
    {
      name: 'Additional Info',
      displayName: 'Additional Info',
      required: false,
      dataType: 'STRING',
      description: 'Additional information about the regulation'
    },
    {
      name: 'Comments Date',
      displayName: 'Comments Date',
      required: false,
      dataType: 'DATE',
      description: 'Comments date (format: 25th May 2018 or YYYY-MM-DD)'
    },
    {
      name: 'Finalisation Date',
      displayName: 'Finalisation Date',
      required: false,
      dataType: 'DATE',
      description: 'Finalisation date (format: 25th May 2018 or YYYY-MM-DD)'
    },
    {
      name: 'Legal Advice',
      displayName: 'Legal Advice',
      required: false,
      dataType: 'STRING',
      description: 'Legal advice text'
    },
    {
      name: 'Parent Regulation Name',
      displayName: 'Parent Regulation Name',
      required: false,
      dataType: 'STRING',
      description: 'Name of the parent regulation (must exist in database)'
    },
    {
      name: 'Parent Ref.',
      displayName: 'Parent Ref.',
      required: false,
      dataType: 'STRING',
      description: 'Reference number of the parent regulation (alternative to name)'
    },
    {
      name: 'BUDG Status',
      displayName: 'BUDG Status',
      required: false,
      dataType: 'STRING',
      description: 'BUDG Status (Active, Obsolete, Deleted) - defaults to 1 if empty',
      aliases: ['Axon Status', 'Status', 'status', 'Object Status']
    },
    {
      name: 'Legal Advice Type',
      displayName: 'Legal Advice Type',
      required: false,
      dataType: 'STRING',
      description: 'Legal advice type (Comment, Guideline, Legal Requirement)'
    },
    {
      name: 'Governance Role',
      displayName: 'Governance Role',
      required: false,
      dataType: 'STRING',
      description: 'Governance role (Regulation Owner, Regulation SME)'
    }
  ],
  DELETE: [
    {
      name: 'Regulation ID',
      displayName: 'Regulation ID',
      required: true,
      dataType: 'INTEGER',
      description: 'Regulation unique identifier to delete (required)'
    }
  ]
};

export const POLICY_FIELDS = {
  INSERT: [
    {
      name: 'Name',
      displayName: 'Name',
      required: true,
      dataType: 'STRING',
      description: 'Primary name of the policy (required, must be unique)'
    },
    {
      name: 'Internal',
      displayName: 'Internal',
      required: true,
      dataType: 'BOOLEAN',
      description: 'Internal flag (required: true/false, yes/no, 1/0)'
    },
    {
      name: 'Description',
      displayName: 'Description',
      required: true,
      dataType: 'STRING',
      description: 'Detailed description of the policy (required)'
    },
    {
      name: 'Lifecycle',
      displayName: 'Lifecycle',
      required: true,
      dataType: 'STRING',
      description: 'Policy lifecycle status (required, defaults to first value if empty)'
    },
    {
      name: 'Type',
      displayName: 'Type',
      required: true,
      dataType: 'STRING',
      description: 'Policy type (required, defaults to first value if empty)'
    },
    {
      name: 'Ref.',
      displayName: 'Ref.',
      required: false,
      dataType: 'STRING',
      description: 'Reference number for the policy (auto-generated as POL-{ID} if empty or POL-1)'
    },
    {
      name: 'URL',
      displayName: 'URL',
      required: false,
      dataType: 'STRING',
      description: 'URL link to the policy document'
    },
    {
      name: 'Effective Date',
      displayName: 'Effective Date',
      required: false,
      dataType: 'DATE',
      description: 'Effective date (format: 25th May 2018 or YYYY-MM-DD)'
    },
    {
      name: 'End Date',
      displayName: 'End Date',
      required: false,
      dataType: 'DATE',
      description: 'End date (format: 25th May 2018 or YYYY-MM-DD)'
    },
    {
      name: 'Parent Name',
      displayName: 'Parent Name',
      required: false,
      dataType: 'STRING',
      description: 'Name of the parent policy (must exist in database)'
    },
    {
      name: 'Parent Ref.',
      displayName: 'Parent Ref.',
      required: false,
      dataType: 'STRING',
      description: 'Reference number of the parent policy (alternative to name)'
    },
    {
      name: 'BUDG Viewing',
      displayName: 'BUDG Viewing',
      required: false,
      dataType: 'STRING',
      description: 'BUDG viewing access level (defaults to first value if empty)',
      aliases: ['Axon Viewing', 'Viewing', 'is_Public', 'Is_Public', 'Public', 'Viewing Access']
    },
    {
      name: 'BUDG Status',
      displayName: 'BUDG Status',
      required: false,
      dataType: 'STRING',
      description: 'BUDG status (defaults to first value if empty)',
      aliases: ['Axon Status', 'Status', 'status', 'Object Status']
    },
    {
      name: 'Lifecycle',
      displayName: 'Lifecycle',
      required: true,
      dataType: 'STRING',
      description: 'Policy lifecycle status (required, defaults to first value if empty)'
    },
    {
      name: 'Type',
      displayName: 'Type',
      required: true,
      dataType: 'STRING',
      description: 'Policy type (required, defaults to first value if empty)'
    },
    {
      name: 'User Email',
      displayName: 'User Email',
      required: false,
      dataType: 'STRING',
      description: 'Stakeholder user email'
    },
    {
      name: 'User First Name',
      displayName: 'User First Name',
      required: false,
      dataType: 'STRING',
      description: 'Stakeholder first name'
    },
    {
      name: 'User Last Name',
      displayName: 'User Last Name',
      required: false,
      dataType: 'STRING',
      description: 'Stakeholder last name'
    },
    {
      name: 'User Lan ID',
      displayName: 'User Lan ID',
      required: false,
      dataType: 'STRING',
      description: 'Stakeholder LAN ID'
    },
    {
      name: 'Governance Role',
      displayName: 'Governance Role',
      required: false,
      dataType: 'STRING',
      description: 'Governance role for stakeholder creation'
    }
  ],
  UPDATE: [
    {
      name: 'ID',
      displayName: 'ID',
      required: true,
      dataType: 'INTEGER',
      description: 'Policy unique identifier (required for updates)'
    },
    {
      name: 'Name',
      displayName: 'Name',
      required: true,
      dataType: 'STRING',
      description: 'Primary name of the policy (required, must be unique)'
    },
    {
      name: 'Ref.',
      displayName: 'Ref.',
      required: true,
      dataType: 'STRING',
      description: 'Reference number for the policy'
    },
    {
      name: 'Internal',
      displayName: 'Internal',
      required: true,
      dataType: 'BOOLEAN',
      description: 'Internal flag (required: true/false, yes/no, 1/0)'
    },
    {
      name: 'URL',
      displayName: 'URL',
      required: true,
      dataType: 'STRING',
      description: 'URL link to the policy document'
    },
    {
      name: 'Description',
      displayName: 'Description',
      required: true,
      dataType: 'STRING',
      description: 'Detailed description of the policy (required)'
    },
    {
      name: 'Effective Date',
      displayName: 'Effective Date',
      required: true,
      dataType: 'DATE',
      description: 'Effective date (format: 25th May 2018 or YYYY-MM-DD)'
    },
    {
      name: 'End Date',
      displayName: 'End Date',
      required: true,
      dataType: 'DATE',
      description: 'End date (format: 25th May 2018 or YYYY-MM-DD)'
    },
    {
      name: 'Parent Name',
      displayName: 'Parent Name',
      required: true,
      dataType: 'STRING',
      description: 'Name of the parent policy (must exist in database)'
    },
    {
      name: 'Parent Ref.',
      displayName: 'Parent Ref.',
      required: true,
      dataType: 'STRING',
      description: 'Reference number of the parent policy (alternative to name)'
    },
    {
      name: 'BUDG Viewing',
      displayName: 'BUDG Viewing',
      required: true,
      dataType: 'STRING',
      description: 'BUDG viewing access level',
      aliases: ['Axon Viewing', 'Viewing', 'is_Public', 'Is_Public', 'Public', 'Viewing Access']
    },
    {
      name: 'BUDG Status',
      displayName: 'BUDG Status',
      required: true,
      dataType: 'STRING',
      description: 'BUDG status',
      aliases: ['Axon Status', 'Status', 'status', 'Object Status']
    },
    {
      name: 'Lifecycle',
      displayName: 'Lifecycle',
      required: true,
      dataType: 'STRING',
      description: 'Policy lifecycle status'
    },
    {
      name: 'Type',
      displayName: 'Type',
      required: true,
      dataType: 'STRING',
      description: 'Policy type'
    }
  ],
  DELETE: [
    {
      name: 'ID',
      displayName: 'ID',
      required: true,
      dataType: 'INTEGER',
      description: 'Policy unique identifier to delete (required)'
    }
  ]
};

export const PROCESS_FIELDS = {
  INSERT: [
    { name: 'Name', displayName: 'Name', required: true, dataType: 'STRING', description: 'Primary name of the process (required)' },
    { name: 'Ref.', displayName: 'Ref.', required: false, dataType: 'STRING', description: 'Reference (auto-generated as PRC-{ID} if empty)' },
    { name: 'Description', displayName: 'Description', required: true, dataType: 'STRING', description: 'Detailed description (required)' },
    { name: 'Input Description', displayName: 'Input Description', required: false, dataType: 'STRING', description: 'Input description' },
    { name: 'Output Description', displayName: 'Output Description', required: false, dataType: 'STRING', description: 'Output description' },
    { name: 'Step Type', displayName: 'Step Type', required: true, dataType: 'STRING', description: 'Step type name from process_step_type (required)' },
    { name: 'Create Permission', displayName: 'Create Permission', required: false, dataType: 'BOOLEAN', description: 'Create permission (true/false, yes/no, 1/0)' },
    { name: 'Read Permission', displayName: 'Read Permission', required: false, dataType: 'BOOLEAN', description: 'Read permission (true/false, yes/no, 1/0)' },
    { name: 'Update Permission', displayName: 'Update Permission', required: false, dataType: 'BOOLEAN', description: 'Update permission (true/false, yes/no, 1/0)' },
    { name: 'Delete Permission', displayName: 'Delete Permission', required: false, dataType: 'BOOLEAN', description: 'Delete permission (true/false, yes/no, 1/0)' },
    { name: 'Archive Permission', displayName: 'Archive Permission', required: false, dataType: 'BOOLEAN', description: 'Archive permission (true/false, yes/no, 1/0)' },
    { name: 'Duration', displayName: 'Duration', required: false, dataType: 'INTEGER', description: 'Duration value' },
    { name: 'Parent Name', displayName: 'Parent Name', required: false, dataType: 'STRING', description: 'Parent process name (alternative to Parent Ref.)' },
    { name: 'Parent Ref.', displayName: 'Parent Ref.', required: false, dataType: 'STRING', description: 'Parent process reference (preferred)' },
    { name: 'BUDG Viewing', displayName: 'BUDG Viewing', required: false, dataType: 'STRING', description: 'Viewing name from viewing table', aliases: ['Axon Viewing', 'Viewing', 'is_Public', 'Is_Public', 'Public', 'Viewing Access'] },
    { name: 'BUDG Status', displayName: 'BUDG Status', required: false, dataType: 'STRING', description: 'Status name from status table', aliases: ['Axon Status', 'Status', 'status', 'Object Status'] },
    { name: 'Type', displayName: 'Type', required: true, dataType: 'STRING', description: 'Process type name from process_type (required)' },
    { name: 'Duration Type', displayName: 'Duration Type', required: false, dataType: 'STRING', description: 'Duration type name from process_duration_type' },
    { name: 'Lifecycle', displayName: 'Lifecycle', required: true, dataType: 'STRING', description: 'Lifecycle name from process_lifecycle_status (required)' },
    { name: 'Classification', displayName: 'Classification', required: false, dataType: 'STRING', description: 'Classification name from process_class' },
    { name: 'Automation', displayName: 'Automation', required: false, dataType: 'STRING', description: 'Automation name from process_automation' },
    { name: 'User Email', displayName: 'User Email', required: false, dataType: 'STRING', description: 'Stakeholder user email' },
    { name: 'User First Name', displayName: 'User First Name', required: false, dataType: 'STRING', description: 'Stakeholder first name' },
    { name: 'User Last Name', displayName: 'User Last Name', required: false, dataType: 'STRING', description: 'Stakeholder last name' },
    { name: 'User Lan ID', displayName: 'User Lan ID', required: false, dataType: 'STRING', description: 'Stakeholder LAN ID' },
    { name: 'Governance Role', displayName: 'Governance Role', required: false, dataType: 'STRING', description: 'Role name from object_role for stakeholder' }
  ],
  UPDATE: [
    { name: 'ID', displayName: 'ID', required: true, dataType: 'INTEGER', description: 'Process unique identifier (required)' },
    { name: 'Name', displayName: 'Name', required: true, dataType: 'STRING', description: 'Primary name of the process (required)' },
    { name: 'Ref.', displayName: 'Ref.', required: false, dataType: 'STRING', description: 'Reference number (must be unique if provided)' },
    { name: 'Description', displayName: 'Description', required: true, dataType: 'STRING', description: 'Detailed description (required)' },
    { name: 'Input Description', displayName: 'Input Description', required: false, dataType: 'STRING', description: 'Input description' },
    { name: 'Output Description', displayName: 'Output Description', required: false, dataType: 'STRING', description: 'Output description' },
    { name: 'Step Type', displayName: 'Step Type', required: true, dataType: 'LIST', description: 'Step type name from process_step_type (required, dropdown)' },
    { name: 'Create Permission', displayName: 'Create Permission', required: false, dataType: 'LIST', description: 'Create permission (dropdown: yes/no)' },
    { name: 'Read Permission', displayName: 'Read Permission', required: false, dataType: 'LIST', description: 'Read permission (dropdown: yes/no)' },
    { name: 'Update Permission', displayName: 'Update Permission', required: false, dataType: 'LIST', description: 'Update permission (dropdown: yes/no)' },
    { name: 'Delete Permission', displayName: 'Delete Permission', required: false, dataType: 'LIST', description: 'Delete permission (dropdown: yes/no)' },
    { name: 'Archive Permission', displayName: 'Archive Permission', required: false, dataType: 'LIST', description: 'Archive permission (dropdown: yes/no)' },
    { name: 'Duration', displayName: 'Duration', required: false, dataType: 'INTEGER', description: 'Duration value' },
    { name: 'Parent Name', displayName: 'Parent Name', required: false, dataType: 'STRING', description: 'Parent process name (alternative to Parent Ref.)' },
    { name: 'Parent Ref.', displayName: 'Parent Ref.', required: false, dataType: 'STRING', description: 'Parent process reference (preferred)' },
    { name: 'BUDG Viewing', displayName: 'BUDG Viewing', required: false, dataType: 'LIST', aliases: ['Axon Viewing', 'Viewing', 'is_Public', 'Is_Public', 'Public', 'Viewing Access'], description: 'Viewing name from viewing table (dropdown)' },
    { name: 'BUDG Status', displayName: 'BUDG Status', required: false, dataType: 'LIST', aliases: ['Axon Status', 'Status', 'status', 'Object Status'], description: 'Status name from status table (dropdown)' },
    { name: 'Type', displayName: 'Type', required: true, dataType: 'LIST', description: 'Process type name from process_type (required, dropdown)' },
    { name: 'Duration Type', displayName: 'Duration Type', required: false, dataType: 'LIST', description: 'Duration type name from process_duration_type (dropdown)' },
    { name: 'Lifecycle', displayName: 'Lifecycle', required: true, dataType: 'LIST', description: 'Lifecycle name from process_lifecycle_status (required, dropdown)' },
    { name: 'Classification', displayName: 'Classification', required: false, dataType: 'LIST', description: 'Classification name from process_class (dropdown)' },
    { name: 'Automation', displayName: 'Automation', required: false, dataType: 'LIST', description: 'Automation name from process_automation (dropdown)' }
  ],
  DELETE: [
    { name: 'ID', displayName: 'ID', required: true, dataType: 'INTEGER', description: 'Process unique identifier to delete (required)' }
  ]
};

export const PROJECT_FIELDS = {
  INSERT: [
    { name: 'Reference', displayName: 'Reference', required: false, dataType: 'STRING', description: 'Project reference number (optional, auto-generated if blank)' },
    { name: 'Project Name', displayName: 'Project Name', required: true, dataType: 'STRING', description: 'Project primary name (required)' },
    { name: 'Project Description', displayName: 'Project Description', required: true, dataType: 'STRING', description: 'Project description (required)' },
    { name: 'Start Date', displayName: 'Start Date', required: true, dataType: 'DATE', description: 'Project start date (required, format: yyyy-MM-dd)' },
    { name: 'End Date', displayName: 'End Date', required: true, dataType: 'DATE', description: 'Project end date (required, format: yyyy-MM-dd)' },
    { name: 'Parent Project Name', displayName: 'Parent Project Name', required: false, dataType: 'STRING', description: 'Parent project name (optional)' },
    { name: 'Parent Ref.', displayName: 'Parent Ref.', required: false, dataType: 'STRING', description: 'Parent project reference (optional)' },
    { name: 'BUDG Viewing', displayName: 'BUDG Viewing', required: false, dataType: 'LIST', aliases: ['Axon Viewing', 'Viewing', 'is_Public', 'Is_Public', 'Public', 'Viewing Access'], description: 'Viewing permission (dropdown from viewing table)' },
    { name: 'RAG', displayName: 'RAG', required: true, dataType: 'LIST', description: 'RAG status (required, dropdown from project_rag table)' },
    { name: 'Classification', displayName: 'Classification', required: false, dataType: 'LIST', description: 'Project classification (dropdown from project_classification table)' },
    { name: 'BUDG Status', displayName: 'BUDG Status', required: false, dataType: 'LIST', aliases: ['Axon Status', 'Status', 'status', 'Object Status'], description: 'Project status (dropdown from status table)' },
    { name: 'Project Lifecycle', displayName: 'Project Lifecycle', required: true, dataType: 'LIST', description: 'Project lifecycle status (required, dropdown from project_lifecycle table)' },
    { name: 'Project Type', displayName: 'Project Type', required: true, dataType: 'LIST', description: 'Project type (required, dropdown from project_comment_type table)' },
    { name: 'User Email', displayName: 'User Email', required: false, dataType: 'STRING', description: 'Stakeholder user email' },
    { name: 'User First Name', displayName: 'User First Name', required: false, dataType: 'STRING', description: 'Stakeholder first name' },
    { name: 'User Last Name', displayName: 'User Last Name', required: false, dataType: 'STRING', description: 'Stakeholder last name' },
    { name: 'User Lan ID', displayName: 'User Lan ID', required: false, dataType: 'STRING', description: 'Stakeholder LAN ID' },
    { name: 'Governance Role', displayName: 'Governance Role', required: false, dataType: 'LIST', description: 'Governance role for stakeholder (dropdown from object_role table)' }
  ],
  UPDATE: [
    { name: 'Project ID', displayName: 'Project ID', required: true, dataType: 'INTEGER', description: 'Project unique identifier (required for updates)' },
    { name: 'Reference', displayName: 'Reference', required: false, dataType: 'STRING', description: 'Project reference number (optional)' },
    { name: 'Project Name', displayName: 'Project Name', required: true, dataType: 'STRING', description: 'Project primary name (required)' },
    { name: 'Project Description', displayName: 'Project Description', required: true, dataType: 'STRING', description: 'Project description (required)' },
    { name: 'Start Date', displayName: 'Start Date', required: true, dataType: 'DATE', description: 'Project start date (required, format: yyyy-MM-dd)' },
    { name: 'End Date', displayName: 'End Date', required: true, dataType: 'DATE', description: 'Project end date (required, format: yyyy-MM-dd)' },
    { name: 'Parent Project Name', displayName: 'Parent Project Name', required: false, dataType: 'STRING', description: 'Parent project name (optional)' },
    { name: 'Parent Ref.', displayName: 'Parent Ref.', required: false, dataType: 'STRING', description: 'Parent project reference (optional)' },
    { name: 'BUDG Viewing', displayName: 'BUDG Viewing', required: false, dataType: 'LIST', aliases: ['Axon Viewing', 'Viewing', 'is_Public', 'Is_Public', 'Public', 'Viewing Access'], description: 'Viewing permission (dropdown from viewing table)' },
    { name: 'RAG', displayName: 'RAG', required: true, dataType: 'LIST', description: 'RAG status (required, dropdown from project_rag table)' },
    { name: 'Classification', displayName: 'Classification', required: false, dataType: 'LIST', description: 'Project classification (dropdown from project_classification table)' },
    { name: 'BUDG Status', displayName: 'BUDG Status', required: false, dataType: 'LIST', aliases: ['Axon Status', 'Status', 'status', 'Object Status'], description: 'Project status (dropdown from status table)' },
    { name: 'Project Lifecycle', displayName: 'Project Lifecycle', required: true, dataType: 'LIST', description: 'Project lifecycle status (required, dropdown from project_lifecycle table)' },
    { name: 'Project Type', displayName: 'Project Type', required: true, dataType: 'LIST', description: 'Project type (required, dropdown from project_comment_type table)' },
    { name: 'Governance Role', displayName: 'Governance Role', required: false, dataType: 'LIST', description: 'Governance role for stakeholder (dropdown from object_role table)' }
  ],
  DELETE: [
    { name: 'Project ID', displayName: 'Project ID', required: true, dataType: 'INTEGER', description: 'Project unique identifier to delete (required)' }
  ]
};

export const SYSTEM_FIELDS = {
  INSERT: [
    { name: 'Short Name', displayName: 'Short Name', required: true, dataType: 'STRING', description: 'Short name of the system (required)' },
    { name: 'Long Name', displayName: 'Long Name', required: false, dataType: 'STRING', description: 'Full name of the system' },
    { name: 'Description', displayName: 'Description', required: true, dataType: 'STRING', description: 'System description (required)' },
    { name: 'Asset ID', displayName: 'Asset ID', required: false, dataType: 'STRING', description: 'Asset identifier' },
    { name: 'URL', displayName: 'URL', required: false, dataType: 'STRING', description: 'System URL' },
    { name: 'External', displayName: 'External', required: true, dataType: 'BOOLEAN', description: 'External system flag (required, default: false)' },
    { name: 'DQAutomation', displayName: 'DQAutomation', required: false, dataType: 'BOOLEAN', description: 'Data quality automation flag' },
    { name: 'Parent Short Name', displayName: 'Parent Short Name', required: false, dataType: 'STRING', description: 'Parent system short name' },
    { name: 'BUDG Viewing', displayName: 'BUDG Viewing', required: false, dataType: 'LIST', aliases: ['Axon Viewing', 'Viewing', 'is_Public', 'Is_Public', 'Public', 'Viewing Access'], description: 'Viewing access level (dropdown from viewing table)' },
    { name: 'BUDG Status', displayName: 'BUDG Status', required: false, dataType: 'LIST', aliases: ['Axon Status', 'Status', 'status', 'Object Status'], description: 'Status (dropdown from status table)' },
    { name: 'Lifecycle', displayName: 'Lifecycle', required: true, dataType: 'LIST', description: 'System lifecycle (required, dropdown from system_lifecycle table)' },
    { name: 'Type', displayName: 'Type', required: true, dataType: 'LIST', description: 'System type (required, dropdown from system_type table)' },
    { name: 'Classification', displayName: 'Classification', required: false, dataType: 'LIST', description: 'System classification (dropdown from system_classification table)' },
    { name: 'Confidentiality', displayName: 'Confidentiality', required: false, dataType: 'LIST', description: 'CIA Confidentiality rating (dropdown from cia_rating table)' },
    { name: 'Integrity', displayName: 'Integrity', required: false, dataType: 'LIST', description: 'CIA Integrity rating (dropdown from cia_rating table)' },
    { name: 'Availability', displayName: 'Availability', required: false, dataType: 'LIST', description: 'CIA Availability rating (dropdown from cia_rating table)' },
    { name: 'User Email', displayName: 'User Email', required: false, dataType: 'STRING', description: 'Stakeholder user email' },
    { name: 'User First Name', displayName: 'User First Name', required: false, dataType: 'STRING', description: 'Stakeholder first name' },
    { name: 'User Last Name', displayName: 'User Last Name', required: false, dataType: 'STRING', description: 'Stakeholder last name' },
    { name: 'User Lan ID', displayName: 'User Lan ID', required: false, dataType: 'STRING', description: 'Stakeholder LAN ID' },
    { name: 'Governance Role', displayName: 'Governance Role', required: false, dataType: 'LIST', description: 'Governance role (dropdown from object_role table)' }
  ],
  UPDATE: [
    { name: 'ID', displayName: 'ID', required: true, dataType: 'INTEGER', description: 'System ID (required for updates)' },
    { name: 'Short Name', displayName: 'Short Name', required: true, dataType: 'STRING', description: 'Short name of the system (required)' },
    { name: 'Long Name', displayName: 'Long Name', required: false, dataType: 'STRING', description: 'Full name of the system' },
    { name: 'Asset ID', displayName: 'Asset ID', required: false, dataType: 'STRING', description: 'Asset identifier' },
    { name: 'External', displayName: 'External', required: true, dataType: 'LIST', description: 'External system flag (required, dropdown: TRUE/FALSE)' },
    { name: 'Description', displayName: 'Description', required: true, dataType: 'STRING', description: 'System description (required)' },
    { name: 'URL', displayName: 'URL', required: false, dataType: 'STRING', description: 'System URL' },
    { name: 'DQAutomation', displayName: 'DQAutomation', required: false, dataType: 'LIST', description: 'Data quality automation flag (dropdown: TRUE/FALSE)' },
    { name: 'Parent Short Name', displayName: 'Parent Short Name', required: false, dataType: 'STRING', description: 'Parent system short name' },
    { name: 'BUDG Viewing', displayName: 'BUDG Viewing', required: false, dataType: 'LIST', aliases: ['Axon Viewing', 'Viewing', 'is_Public', 'Is_Public', 'Public', 'Viewing Access'], description: 'Viewing access level (dropdown from viewing table)' },
    { name: 'BUDG Status', displayName: 'BUDG Status', required: false, dataType: 'LIST', aliases: ['Axon Status', 'Status', 'status', 'Object Status'], description: 'Status (dropdown from status table)' },
    { name: 'Lifecycle', displayName: 'Lifecycle', required: true, dataType: 'LIST', description: 'System lifecycle (required, dropdown from system_lifecycle table)' },
    { name: 'Type', displayName: 'Type', required: true, dataType: 'LIST', description: 'System type (required, dropdown from system_type table)' },
    { name: 'Classification', displayName: 'Classification', required: false, dataType: 'LIST', description: 'System classification (dropdown from system_classification table)' },
    { name: 'Confidentiality', displayName: 'Confidentiality', required: false, dataType: 'LIST', description: 'CIA Confidentiality rating (dropdown from cia_rating table)' },
    { name: 'Integrity', displayName: 'Integrity', required: false, dataType: 'LIST', description: 'CIA Integrity rating (dropdown from cia_rating table)' },
    { name: 'Availability', displayName: 'Availability', required: false, dataType: 'LIST', description: 'CIA Availability rating (dropdown from cia_rating table)' }
  ],
  DELETE: [
    { name: 'ID', displayName: 'ID', required: true, dataType: 'INTEGER', description: 'System ID to delete' }
  ]
};

export const DATASET_FIELDS = {
  INSERT: [
    { name: 'Ref.', displayName: 'Ref.', required: false, dataType: 'STRING', description: 'Reference number (auto-generated if empty)' },
    { name: 'Name', displayName: 'Name', required: true, dataType: 'STRING', description: 'Dataset name (required)' },
    { name: 'Definition', displayName: 'Definition', required: true, dataType: 'STRING', description: 'Dataset definition (required)' },
    { name: 'Usage', displayName: 'Usage', required: false, dataType: 'STRING', description: 'Dataset usage description' },
    { name: 'BUDG Viewing', displayName: 'BUDG Viewing', required: false, dataType: 'LIST', aliases: ['Axon Viewing', 'Viewing', 'is_Public', 'Is_Public', 'Public', 'Viewing Access'], description: 'Viewing access level (dropdown from viewing table)' },
    { name: 'Type', displayName: 'Type', required: true, dataType: 'LIST', description: 'Dataset type (required, dropdown from dataset_type table)' },
    { name: 'System ID', displayName: 'System ID', required: false, dataType: 'INTEGER', description: 'System ID (direct reference). You must provide System ID or System Short Name for new datasets.' },
    { name: 'System Short Name', displayName: 'System Short Name', required: false, dataType: 'STRING', description: 'System short name. You must provide System ID or System Short Name for new datasets. Use with Parent System Short Name if multiple systems share the same name.' },
    { name: 'Parent System Short Name', displayName: 'Parent System Short Name', required: false, dataType: 'STRING', description: 'Parent system short name (disambiguation). Only valid together with System ID or System Short Name.' },
    { name: 'Lifecycle', displayName: 'Lifecycle', required: true, dataType: 'LIST', description: 'Dataset lifecycle (required, dropdown from dataset_lifecycle table)' },
    { name: 'BUDG Status', displayName: 'BUDG Status', required: false, dataType: 'LIST', aliases: ['Axon Status', 'Status', 'status', 'Object Status'], description: 'Status (dropdown from status table)' },
    { name: 'Glossary Ref.', displayName: 'Glossary Ref.', required: false, dataType: 'STRING', description: 'Glossary reference number. If both Ref. and Name are provided, they must refer to the same term.' },
    { name: 'Glossary Name', displayName: 'Glossary Name', required: false, dataType: 'STRING', description: 'Glossary term name. Use with Parent Glossary Name if multiple terms share the same name.' },
    { name: 'Parent Glossary Name', displayName: 'Parent Glossary Name', required: false, dataType: 'STRING', description: 'Immediate parent glossary term name (disambiguation). Only valid together with Glossary Ref. or Glossary Name.' },
    { name: 'User Email', displayName: 'User Email', required: false, dataType: 'STRING', description: 'Stakeholder user email' },
    { name: 'User First Name', displayName: 'User First Name', required: false, dataType: 'STRING', description: 'Stakeholder first name' },
    { name: 'User Last Name', displayName: 'User Last Name', required: false, dataType: 'STRING', description: 'Stakeholder last name' },
    { name: 'User Lan ID', displayName: 'User Lan ID', required: false, dataType: 'STRING', description: 'Stakeholder LAN ID' },
    { name: 'Governance Role', displayName: 'Governance Role', required: false, dataType: 'LIST', description: 'Governance role (dropdown from object_role table)' }
  ],
  UPDATE: [
    { name: 'ID', displayName: 'ID', required: true, dataType: 'INTEGER', description: 'Dataset ID (required for updates)' },
    { name: 'Ref.', displayName: 'Ref.', required: false, dataType: 'STRING', description: 'Reference number' },
    { name: 'Name', displayName: 'Name', required: true, dataType: 'STRING', description: 'Dataset name (required)' },
    { name: 'Definition', displayName: 'Definition', required: true, dataType: 'STRING', description: 'Dataset definition (required)' },
    { name: 'Usage', displayName: 'Usage', required: false, dataType: 'STRING', description: 'Dataset usage description' },
    { name: 'BUDG Viewing', displayName: 'BUDG Viewing', required: false, dataType: 'LIST', aliases: ['Axon Viewing', 'Viewing', 'is_Public', 'Is_Public', 'Public', 'Viewing Access'], description: 'Viewing access level (dropdown from viewing table)' },
    { name: 'Type', displayName: 'Type', required: true, dataType: 'LIST', description: 'Dataset type (required, dropdown from dataset_type table)' },
    { name: 'System ID', displayName: 'System ID', required: false, dataType: 'INTEGER', description: 'System ID (direct reference)' },
    { name: 'System Short Name', displayName: 'System Short Name', required: false, dataType: 'STRING', description: 'System short name (alternative to System ID)' },
    { name: 'Parent System Short Name', displayName: 'Parent System Short Name', required: false, dataType: 'STRING', description: 'Parent system short name' },
    { name: 'Lifecycle', displayName: 'Lifecycle', required: true, dataType: 'LIST', description: 'Dataset lifecycle (required, dropdown from dataset_lifecycle table)' },
    { name: 'BUDG Status', displayName: 'BUDG Status', required: false, dataType: 'LIST', aliases: ['Axon Status', 'Status', 'status', 'Object Status'], description: 'Status (dropdown from status table)' },
    { name: 'Glossary Ref.', displayName: 'Glossary Ref.', required: false, dataType: 'STRING', description: 'Glossary reference number' },
    { name: 'Glossary Name', displayName: 'Glossary Name', required: false, dataType: 'STRING', description: 'Glossary name (alternative to Glossary Ref.)' },
    { name: 'Parent Glossary Name', displayName: 'Parent Glossary Name', required: false, dataType: 'STRING', description: 'Parent glossary name' }
  ],
  DELETE: [
    { name: 'ID', displayName: 'ID', required: true, dataType: 'INTEGER', description: 'Dataset ID to delete' }
  ]
};

export const ATTRIBUTE_FIELDS = {
  INSERT: [
    { name: 'Reference Number', displayName: 'Reference Number', required: false, dataType: 'STRING', description: 'Reference number (auto-generated if empty)' },
    { name: 'Attribute Name', displayName: 'Attribute Name', required: true, dataType: 'STRING', description: 'Attribute name (required)' },
    { name: 'Attribute Definition', displayName: 'Attribute Definition', required: true, dataType: 'STRING', description: 'Attribute definition (required)' },
    { name: 'Key', displayName: 'Key', required: false, dataType: 'BOOLEAN', description: 'Is Primary Key (TRUE/FALSE)' },
    { name: 'Business Logic', displayName: 'Business Logic', required: false, dataType: 'STRING', description: 'Business logic description' },
    { name: 'Data Length', displayName: 'Data Length', required: false, dataType: 'INTEGER', description: 'Data length' },
    { name: 'Data Type', displayName: 'Data Type', required: false, dataType: 'LIST', description: 'Data type (dropdown from attribute_datatype table)' },
    { name: 'Attribute Requirement', displayName: 'Attribute Requirement', required: false, dataType: 'LIST', description: 'Attribute requirement (dropdown from requirement table)' },
    { name: 'Data Set Ref.', displayName: 'Data Set Ref.', required: false, dataType: 'STRING', description: 'Dataset reference number' },
    { name: 'Data Set Name', displayName: 'Data Set Name', required: false, dataType: 'STRING', description: 'Dataset name (alternative to Data Set Ref.)' },
    { name: 'System Short Name', displayName: 'System Short Name', required: false, dataType: 'STRING', description: 'System short name (alternative to find dataset)' },
    { name: 'Glossary Ref.', displayName: 'Glossary Ref.', required: false, dataType: 'STRING', description: 'Glossary reference number' },
    { name: 'Glossary Name', displayName: 'Glossary Name', required: false, dataType: 'STRING', description: 'Glossary name (alternative to Glossary Ref.)' },
    { name: 'Parent Glossary Name', displayName: 'Parent Glossary Name', required: false, dataType: 'STRING', description: 'Parent glossary name' },
    { name: 'Origin', displayName: 'Origin', required: false, dataType: 'LIST', description: 'Origin (dropdown from attribute_origination table)' },
    { name: 'Editability', displayName: 'Editability', required: false, dataType: 'LIST', description: 'Editability (dropdown from attribute_editability table)' },
    { name: 'Editability Role', displayName: 'Editability Role', required: false, dataType: 'LIST', description: 'Editability role (dropdown from attribute_edit_role table)' },
    { name: 'DB Field Name', displayName: 'DB Field Name', required: false, dataType: 'STRING', description: 'Database field name' },
    { name: 'User Email', displayName: 'User Email', required: false, dataType: 'STRING', description: 'Stakeholder user email' },
    { name: 'User First Name', displayName: 'User First Name', required: false, dataType: 'STRING', description: 'Stakeholder first name' },
    { name: 'User Last Name', displayName: 'User Last Name', required: false, dataType: 'STRING', description: 'Stakeholder last name' },
    { name: 'User Lan ID', displayName: 'User Lan ID', required: false, dataType: 'STRING', description: 'Stakeholder LAN ID' },
    { name: 'Governance Role', displayName: 'Governance Role', required: false, dataType: 'LIST', description: 'Governance role (dropdown from object_role table)' }
  ],
  UPDATE: [
    { name: 'Attribute ID', displayName: 'Attribute ID', required: true, dataType: 'INTEGER', description: 'Attribute ID (required for updates)' },
    { name: 'Reference Number', displayName: 'Reference Number', required: false, dataType: 'STRING', description: 'Reference number' },
    { name: 'Attribute Name', displayName: 'Attribute Name', required: true, dataType: 'STRING', description: 'Attribute name (required)' },
    { name: 'Attribute Definition', displayName: 'Attribute Definition', required: true, dataType: 'STRING', description: 'Attribute definition (required)' },
    { name: 'Key', displayName: 'Key', required: false, dataType: 'BOOLEAN', description: 'Is Primary Key (TRUE/FALSE)' },
    { name: 'Business Logic', displayName: 'Business Logic', required: false, dataType: 'STRING', description: 'Business logic description' },
    { name: 'Data Length', displayName: 'Data Length', required: false, dataType: 'INTEGER', description: 'Data length' },
    { name: 'Data Type', displayName: 'Data Type', required: false, dataType: 'LIST', description: 'Data type (dropdown from attribute_datatype table)' },
    { name: 'Attribute Requirement', displayName: 'Attribute Requirement', required: false, dataType: 'LIST', description: 'Attribute requirement (dropdown from requirement table)' },
    { name: 'Data Set Ref.', displayName: 'Data Set Ref.', required: false, dataType: 'STRING', description: 'Dataset reference number' },
    { name: 'Data Set Name', displayName: 'Data Set Name', required: false, dataType: 'STRING', description: 'Dataset name (alternative to Data Set Ref.)' },
    { name: 'System Short Name', displayName: 'System Short Name', required: false, dataType: 'STRING', description: 'System short name (alternative to find dataset)' },
    { name: 'Glossary Ref.', displayName: 'Glossary Ref.', required: false, dataType: 'STRING', description: 'Glossary reference number' },
    { name: 'Glossary Name', displayName: 'Glossary Name', required: false, dataType: 'STRING', description: 'Glossary name (alternative to Glossary Ref.)' },
    { name: 'Parent Glossary Name', displayName: 'Parent Glossary Name', required: false, dataType: 'STRING', description: 'Parent glossary name' },
    { name: 'Origin', displayName: 'Origin', required: false, dataType: 'LIST', description: 'Origin (dropdown from attribute_origination table)' },
    { name: 'Editability', displayName: 'Editability', required: false, dataType: 'LIST', description: 'Editability (dropdown from attribute_editability table)' },
    { name: 'Editability Role', displayName: 'Editability Role', required: false, dataType: 'LIST', description: 'Editability role (dropdown from attribute_edit_role table)' },
    { name: 'DB Field Name', displayName: 'DB Field Name', required: false, dataType: 'STRING', description: 'Database field name' }
  ],
  DELETE: [
    { name: 'Attribute ID', displayName: 'Attribute ID', required: true, dataType: 'INTEGER', description: 'Attribute ID to delete' }
  ]
};

export const INTERFACE_FIELDS = {
  INSERT: [
    { name: 'Interface Name', displayName: 'Interface Name', required: true, dataType: 'STRING', description: 'Interface name (required)' },
    { name: 'Reference', displayName: 'Reference', required: false, dataType: 'STRING', description: 'Reference number (auto-generated if empty)' },
    { name: 'Asset ID', displayName: 'Asset ID', required: false, dataType: 'STRING', description: 'Asset identifier' },
    { name: 'Synchronisation Control', displayName: 'Synchronisation Control', required: false, dataType: 'STRING', description: 'Synchronisation control information' },
    { name: 'Interface Description', displayName: 'Interface Description', required: true, dataType: 'STRING', description: 'Interface description (required)' },
    { name: 'Transfer Method', displayName: 'Transfer Method', required: false, dataType: 'LIST', description: 'Transfer method (dropdown from interface_transfer table)' },
    { name: 'Transfer Format', displayName: 'Transfer Format', required: false, dataType: 'LIST', description: 'Transfer format (dropdown from interface_transfer_format table)' },
    { name: 'Interface Classification', displayName: 'Interface Classification', required: false, dataType: 'LIST', description: 'Interface classification (dropdown from interface_classification table)' },
    { name: 'Lifecycle', displayName: 'Lifecycle', required: true, dataType: 'LIST', description: 'Interface lifecycle (required, dropdown from interface_lifecycle table)' },
    { name: 'BUDG Status', displayName: 'BUDG Status', required: false, dataType: 'LIST', aliases: ['Axon Status', 'Status', 'status', 'Object Status'], description: 'Status (dropdown from status table)' },
    { name: 'Source System Short Name', displayName: 'Source System Short Name', required: true, dataType: 'STRING', description: 'Source system short name (required)' },
    { name: 'Target System Short Name', displayName: 'Target System Short Name', required: true, dataType: 'STRING', description: 'Target system short name (required)' },
    { name: 'Automation Level', displayName: 'Automation Level', required: true, dataType: 'LIST', description: 'Automation level (required, dropdown from interface_automation table)' },
    { name: 'Frequency', displayName: 'Frequency', required: false, dataType: 'LIST', description: 'Frequency (dropdown from interface_frequency table)' },
    { name: 'BUDG Viewing', displayName: 'BUDG Viewing', required: false, dataType: 'LIST', aliases: ['Axon Viewing', 'Viewing', 'is_Public', 'Is_Public', 'Public', 'Viewing Access'], description: 'Viewing access level (dropdown from viewing table)' },
    { name: 'User Email', displayName: 'User Email', required: false, dataType: 'STRING', description: 'Stakeholder user email' },
    { name: 'User First Name', displayName: 'User First Name', required: false, dataType: 'STRING', description: 'Stakeholder first name' },
    { name: 'User Last Name', displayName: 'User Last Name', required: false, dataType: 'STRING', description: 'Stakeholder last name' },
    { name: 'User Lan ID', displayName: 'User Lan ID', required: false, dataType: 'STRING', description: 'Stakeholder LAN ID' },
    { name: 'Governance Role', displayName: 'Governance Role', required: false, dataType: 'LIST', description: 'Governance role (dropdown from object_role table)' }
  ],
  UPDATE: [
    { name: 'Interface ID', displayName: 'Interface ID', required: true, dataType: 'INTEGER', description: 'Interface ID (required for updates)' },
    { name: 'Interface Name', displayName: 'Interface Name', required: true, dataType: 'STRING', description: 'Interface name (required)' },
    { name: 'Reference', displayName: 'Reference', required: false, dataType: 'STRING', description: 'Reference number' },
    { name: 'Asset ID', displayName: 'Asset ID', required: false, dataType: 'STRING', description: 'Asset identifier' },
    { name: 'Synchronisation Control', displayName: 'Synchronisation Control', required: false, dataType: 'STRING', description: 'Synchronisation control information' },
    { name: 'Interface Description', displayName: 'Interface Description', required: true, dataType: 'STRING', description: 'Interface description (required)' },
    { name: 'Transfer Method', displayName: 'Transfer Method', required: false, dataType: 'LIST', description: 'Transfer method (dropdown from interface_transfer table)' },
    { name: 'Transfer Format', displayName: 'Transfer Format', required: false, dataType: 'LIST', description: 'Transfer format (dropdown from interface_transfer_format table)' },
    { name: 'Interface Classification', displayName: 'Interface Classification', required: false, dataType: 'LIST', description: 'Interface classification (dropdown from interface_classification table)' },
    { name: 'Lifecycle', displayName: 'Lifecycle', required: true, dataType: 'LIST', description: 'Interface lifecycle (required, dropdown from interface_lifecycle table)' },
    { name: 'BUDG Status', displayName: 'BUDG Status', required: false, dataType: 'LIST', aliases: ['Axon Status', 'Status', 'status', 'Object Status'], description: 'Status (dropdown from status table)' },
    { name: 'Source System Short Name', displayName: 'Source System Short Name', required: true, dataType: 'STRING', description: 'Source system short name (required)' },
    { name: 'Target System Short Name', displayName: 'Target System Short Name', required: true, dataType: 'STRING', description: 'Target system short name (required)' },
    { name: 'Automation Level', displayName: 'Automation Level', required: true, dataType: 'LIST', description: 'Automation level (required, dropdown from interface_automation table)' },
    { name: 'Frequency', displayName: 'Frequency', required: false, dataType: 'LIST', description: 'Frequency (dropdown from interface_frequency table)' },
    { name: 'BUDG Viewing', displayName: 'BUDG Viewing', required: false, dataType: 'LIST', aliases: ['Axon Viewing', 'Viewing', 'is_Public', 'Is_Public', 'Public', 'Viewing Access'], description: 'Viewing access level (dropdown from viewing table)' }
  ],
  DELETE: [
    { name: 'ID', displayName: 'ID', required: true, dataType: 'INTEGER', description: 'Interface ID to delete' }
  ]
};

export const GLOSSARY_FIELDS = {
  INSERT: [
    { name: 'Name', displayName: 'Name', required: true, dataType: 'STRING', description: 'Glossary term name (required)' },
    { name: 'Definition', displayName: 'Definition', required: true, dataType: 'STRING', description: 'Glossary term definition (required)' },
    { name: 'Ref.', displayName: 'Ref.', required: false, dataType: 'STRING', description: 'Reference number (auto-generated if empty)' },
    { name: 'Examples', displayName: 'Examples', required: false, dataType: 'STRING', description: 'Examples of usage' },
    { name: 'Business Logic', displayName: 'Business Logic', required: false, dataType: 'STRING', description: 'Business logic description' },
    { name: 'Format Description', displayName: 'Format Description', required: false, dataType: 'STRING', description: 'Format description' },
    { name: 'LDM Reference', displayName: 'LDM Reference', required: false, dataType: 'STRING', description: 'LDM reference' },
    { name: 'Parent Name', displayName: 'Parent Name', required: false, dataType: 'STRING', description: 'Parent glossary name' },
    { name: 'Parent Ref.', displayName: 'Parent Ref.', required: false, dataType: 'STRING', description: 'Parent glossary reference' },
    { name: 'BUDG Viewing', displayName: 'BUDG Viewing', required: false, dataType: 'LIST', aliases: ['Axon Viewing', 'Viewing', 'is_Public', 'Is_Public', 'Public', 'Viewing Access'], description: 'Viewing access level (dropdown from viewing table)' },
    { name: 'BUDG Status', displayName: 'BUDG Status', required: false, dataType: 'LIST', aliases: ['Axon Status', 'Status', 'status', 'Object Status'], description: 'Status (dropdown from status table)' },
    { name: 'Lifecycle', displayName: 'Lifecycle', required: false, dataType: 'LIST', description: 'Glossary lifecycle (dropdown from glossary_lifecycle table)' },
    { name: 'Format Type', displayName: 'Format Type', required: false, dataType: 'LIST', description: 'Format type (dropdown from glossary_format_type table)' },
    { name: 'KDE', displayName: 'KDE', required: false, dataType: 'LIST', description: 'KDE (dropdown from glossary_kde_type table)' },
    { name: 'Security Classification', displayName: 'Security Classification', required: false, dataType: 'LIST', description: 'Security classification (dropdown from security_classification table)' },
    { name: 'Type', displayName: 'Type', required: false, dataType: 'LIST', description: 'Glossary type (dropdown from glossary_type table)' },
    { name: 'Confidentiality', displayName: 'Confidentiality', required: false, dataType: 'LIST', description: 'Confidentiality rating (dropdown from cia_rating table)' },
    { name: 'Integrity', displayName: 'Integrity', required: false, dataType: 'LIST', description: 'Integrity rating (dropdown from cia_rating table)' },
    { name: 'Availability', displayName: 'Availability', required: false, dataType: 'LIST', description: 'Availability rating (dropdown from cia_rating table)' },
    { name: 'Alias Names', displayName: 'Alias Names', required: false, dataType: 'STRING', description: 'Alias names' },
    { name: 'User Email', displayName: 'User Email', required: false, dataType: 'STRING', description: 'Stakeholder user email' },
    { name: 'User First Name', displayName: 'User First Name', required: false, dataType: 'STRING', description: 'Stakeholder first name' },
    { name: 'User Last Name', displayName: 'User Last Name', required: false, dataType: 'STRING', description: 'Stakeholder last name' },
    { name: 'User Lan ID', displayName: 'User Lan ID', required: false, dataType: 'STRING', description: 'Stakeholder LAN ID' },
    { name: 'Governance Role', displayName: 'Governance Role', required: false, dataType: 'LIST', description: 'Governance role (dropdown from object_role table)' }
  ],
  UPDATE: [
    { name: 'ID', displayName: 'ID', required: true, dataType: 'INTEGER', description: 'Glossary ID (required for updates)' },
    { name: 'Name', displayName: 'Name', required: true, dataType: 'STRING', description: 'Glossary term name (required)' },
    { name: 'Definition', displayName: 'Definition', required: true, dataType: 'STRING', description: 'Glossary term definition (required)' },
    { name: 'Ref.', displayName: 'Ref.', required: false, dataType: 'STRING', description: 'Reference number' },
    { name: 'Examples', displayName: 'Examples', required: false, dataType: 'STRING', description: 'Examples of usage' },
    { name: 'Business Logic', displayName: 'Business Logic', required: false, dataType: 'STRING', description: 'Business logic description' },
    { name: 'Format Description', displayName: 'Format Description', required: false, dataType: 'STRING', description: 'Format description' },
    { name: 'LDM Reference', displayName: 'LDM Reference', required: false, dataType: 'STRING', description: 'LDM reference' },
    { name: 'Parent Name', displayName: 'Parent Name', required: false, dataType: 'STRING', description: 'Parent glossary name' },
    { name: 'Parent Ref.', displayName: 'Parent Ref.', required: false, dataType: 'STRING', description: 'Parent glossary reference' },
    { name: 'BUDG Viewing', displayName: 'BUDG Viewing', required: false, dataType: 'LIST', aliases: ['Axon Viewing', 'Viewing', 'is_Public', 'Is_Public', 'Public', 'Viewing Access'], description: 'Viewing access level (dropdown from viewing table)' },
    { name: 'BUDG Status', displayName: 'BUDG Status', required: false, dataType: 'LIST', aliases: ['Axon Status', 'Status', 'status', 'Object Status'], description: 'Status (dropdown from status table)' },
    { name: 'Lifecycle', displayName: 'Lifecycle', required: true, dataType: 'LIST', description: 'Glossary lifecycle (required, dropdown from glossary_lifecycle table)' },
    { name: 'Format Type', displayName: 'Format Type', required: true, dataType: 'LIST', description: 'Format type (required, dropdown from glossary_format_type table)' },
    { name: 'KDE', displayName: 'KDE', required: false, dataType: 'LIST', description: 'KDE (dropdown from glossary_kde_type table)' },
    { name: 'Security Classification', displayName: 'Security Classification', required: true, dataType: 'LIST', description: 'Security classification (required, dropdown from security_classification table)' },
    { name: 'Type', displayName: 'Type', required: true, dataType: 'LIST', description: 'Glossary type (required, dropdown from glossary_type table)' },
    { name: 'Confidentiality', displayName: 'Confidentiality', required: false, dataType: 'LIST', description: 'CIA Confidentiality rating (dropdown from cia_rating table)' },
    { name: 'Integrity', displayName: 'Integrity', required: false, dataType: 'LIST', description: 'CIA Integrity rating (dropdown from cia_rating table)' },
    { name: 'Availability', displayName: 'Availability', required: false, dataType: 'LIST', description: 'CIA Availability rating (dropdown from cia_rating table)' },
    { name: 'Alias Names', displayName: 'Alias Names', required: false, dataType: 'STRING', description: 'Alias names (comma-separated)' }
  ],
  DELETE: [
    { name: 'ID', displayName: 'ID', required: true, dataType: 'INTEGER', description: 'Glossary ID to delete' }
  ]
};

export const COMMITTEE_FIELDS = {
  INSERT: [
    { name: 'Reference', displayName: 'Reference', required: false, dataType: 'STRING', description: 'Committee reference number (optional, auto-generated if blank)' },
    { name: 'Committee Name', displayName: 'Committee Name', required: true, dataType: 'STRING', description: 'Committee primary name (required)' },
    { name: 'Description', displayName: 'Description', required: true, dataType: 'STRING', description: 'Committee description (required)' },
    { name: 'Parent Committee Name', displayName: 'Parent Committee Name', required: false, dataType: 'STRING', description: 'Parent committee name (optional)' },
    { name: 'Parent Ref.', displayName: 'Parent Ref.', required: false, dataType: 'STRING', description: 'Parent committee reference (optional)' },
    { name: 'BUDG Viewing', displayName: 'BUDG Viewing', required: false, dataType: 'LIST', aliases: ['Axon Viewing', 'Viewing', 'is_Public', 'Is_Public', 'Public', 'Viewing Access'], description: 'Viewing permission (dropdown from viewing table, defaults to first if empty)' },
    { name: 'Classification', displayName: 'Classification', required: true, dataType: 'LIST', description: 'Committee classification (required, dropdown from committee_classification table, defaults to first if empty)' },
    { name: 'BUDG Status', displayName: 'BUDG Status', required: false, dataType: 'LIST', aliases: ['Axon Status', 'Status', 'status', 'Object Status'], description: 'Committee status (dropdown from status table, defaults to first if empty)' },
    { name: 'Lifecycle', displayName: 'Lifecycle', required: true, dataType: 'LIST', description: 'Committee lifecycle status (required, dropdown from committee_lifecycle table, defaults to first if empty)' },
    { name: 'Committee Type', displayName: 'Committee Type', required: true, dataType: 'LIST', description: 'Committee type (required, dropdown from committee_type table, defaults to first if empty)' },
    { name: 'User Email', displayName: 'User Email', required: false, dataType: 'STRING', description: 'Stakeholder user email' },
    { name: 'User First Name', displayName: 'User First Name', required: false, dataType: 'STRING', description: 'Stakeholder first name' },
    { name: 'User Last Name', displayName: 'User Last Name', required: false, dataType: 'STRING', description: 'Stakeholder last name' },
    { name: 'User Lan ID', displayName: 'User Lan ID', required: false, dataType: 'STRING', description: 'Stakeholder LAN ID' },
    { name: 'Governance Role', displayName: 'Governance Role', required: false, dataType: 'LIST', description: 'Governance role for stakeholder (dropdown from object_role table)' }
  ],
  UPDATE: [
    { name: 'Committee ID', displayName: 'Committee ID', required: true, dataType: 'INTEGER', description: 'Committee unique identifier (required for updates)' },
    { name: 'Committee Name', displayName: 'Committee Name', required: false, dataType: 'STRING', description: 'Committee primary name (optional)' },
    { name: 'Description', displayName: 'Description', required: false, dataType: 'STRING', description: 'Committee description (optional)' },
    { name: 'Reference', displayName: 'Reference', required: false, dataType: 'STRING', description: 'Committee reference number (optional)' },
    { name: 'Parent Committee Name', displayName: 'Parent Committee Name', required: false, dataType: 'STRING', description: 'Parent committee name (optional)' },
    { name: 'Parent Ref.', displayName: 'Parent Ref.', required: false, dataType: 'STRING', description: 'Parent committee reference (optional)' },
    { name: 'BUDG Viewing', displayName: 'BUDG Viewing', required: false, dataType: 'LIST', aliases: ['Axon Viewing', 'Viewing', 'is_Public', 'Is_Public', 'Public', 'Viewing Access'], description: 'Viewing permission (dropdown from viewing table)' },
    { name: 'Classification', displayName: 'Classification', required: false, dataType: 'LIST', description: 'Committee classification (dropdown from committee_classification table)' },
    { name: 'BUDG Status', displayName: 'BUDG Status', required: false, dataType: 'LIST', aliases: ['Axon Status', 'Status', 'status', 'Object Status'], description: 'Committee status (dropdown from status table)' },
    { name: 'Lifecycle', displayName: 'Lifecycle', required: false, dataType: 'LIST', description: 'Committee lifecycle status (dropdown from committee_lifecycle table)' },
    { name: 'Committee Type', displayName: 'Committee Type', required: false, dataType: 'LIST', description: 'Committee type (dropdown from committee_type table)' }
  ],
  DELETE: [
    { name: 'Committee ID', displayName: 'Committee ID', required: true, dataType: 'INTEGER', description: 'Committee unique identifier to delete (required)' }
  ]
};

export function getFieldsForEntity(entity, operation) {
  const normalizedOperation = operation.toUpperCase();
  
  switch (entity) {
    case 'Regulator':
      return REGULATOR_FIELDS[normalizedOperation] || [];
    case 'Geography':
      return GEOGRAPHY_FIELDS[normalizedOperation] || [];
    case 'Regulatory Theme':
      return REGULATORY_THEME_FIELDS[normalizedOperation] || [];
    case 'Regulation':
      return REGULATION_FIELDS[normalizedOperation] || [];
    case 'Policy':
      return POLICY_FIELDS[normalizedOperation] || [];
    case 'Process':
      return PROCESS_FIELDS[normalizedOperation] || [];
    case 'Project':
      return PROJECT_FIELDS[normalizedOperation] || [];
    case 'Committee':
      return COMMITTEE_FIELDS[normalizedOperation] || [];
    case 'Business Area':
      return BUSINESS_AREA_FIELDS[normalizedOperation] || [];
    case 'Capability':
      return CAPABILITY_FIELDS[normalizedOperation] || [];
    case 'Client':
      return CLIENT_FIELDS[normalizedOperation] || [];
    case 'Legal':
      return LEGAL_FIELDS[normalizedOperation] || [];
    case 'Org. Unit':
      return ORG_UNIT_FIELDS[normalizedOperation] || [];
    case 'People':
      return PEOPLE_FIELDS[normalizedOperation] || [];
    case 'Product':
      return PRODUCT_FIELDS[normalizedOperation] || [];
    case 'System':
      return SYSTEM_FIELDS[normalizedOperation] || [];
    case 'Dataset':
      return DATASET_FIELDS[normalizedOperation] || [];
    case 'Attribute':
      return ATTRIBUTE_FIELDS[normalizedOperation] || [];
    case 'Interface':
      return INTERFACE_FIELDS[normalizedOperation] || [];
    case 'Glossary':
      return GLOSSARY_FIELDS[normalizedOperation] || [];
    
    // Role entities
    case 'Business Area Role':
      return BUSINESS_AREA_ROLE_FIELDS[normalizedOperation] || [];
    case 'Capability Role':
      return CAPABILITY_ROLE_FIELDS[normalizedOperation] || [];
    case 'Client Role':
      return CLIENT_ROLE_FIELDS[normalizedOperation] || [];
    case 'Committee Role':
      return COMMITTEE_ROLE_FIELDS[normalizedOperation] || [];
    case 'Data Quality Role':
      return DATA_QUALITY_ROLE_FIELDS[normalizedOperation] || [];
    case 'Data Set Role':
      return DATA_SET_ROLE_FIELDS[normalizedOperation] || [];
    case 'Glossary Role':
      return GLOSSARY_ROLE_FIELDS[normalizedOperation] || [];
    case 'Interface Role':
      return INTERFACE_ROLE_FIELDS[normalizedOperation] || [];
    case 'Legal Entity Role':
      return LEGAL_ENTITY_ROLE_FIELDS[normalizedOperation] || [];
    case 'Policy Role':
      return POLICY_ROLE_FIELDS[normalizedOperation] || [];
    case 'Process Role':
      return PROCESS_ROLE_FIELDS[normalizedOperation] || [];
    case 'Product Role':
      return PRODUCT_ROLE_FIELDS[normalizedOperation] || [];
    case 'Project Role':
      return PROJECT_ROLE_FIELDS[normalizedOperation] || [];
    case 'Regulation Role':
      return REGULATION_ROLE_FIELDS[normalizedOperation] || [];
    case 'System Role':
      return SYSTEM_ROLE_FIELDS[normalizedOperation] || [];
    
    // Relationship entities
    default:
      // Check if it's a relationship entity
      if (RELATIONSHIP_FIELD_MAP[entity]) {
        return RELATIONSHIP_FIELD_MAP[entity][normalizedOperation] || [];
      }
      return [];
  }
}

/**
 * Map upload option to operation type
 */
export function getOperationType(uploadOption) {
  if (uploadOption === 'Add New Items') return 'INSERT';
  if (uploadOption === 'Update Existing Items') return 'UPDATE';
  if (uploadOption === 'Remove Existing Items' || uploadOption === 'Delete Items') return 'DELETE';
  return 'INSERT';
}



// ===== NEW ENTITIES ADDED FOR BULK UPLOAD =====

export const BUSINESS_AREA_FIELDS = {
  INSERT: [
    { name: 'Business Area Name', displayName: 'Business Area Name', required: true, dataType: 'STRING', description: 'Primary name of the business area (required)' },
    { name: 'Description', displayName: 'Description', required: false, dataType: 'STRING', description: 'Detailed description' },
    { name: 'BUDG Viewing', displayName: 'BUDG Viewing', required: false, dataType: 'LIST', aliases: ['Axon Viewing', 'Viewing', 'is_Public', 'Is_Public', 'Public', 'Viewing Access'], description: 'Viewing access level (dropdown from viewing table)' },
    { name: 'BUDG Status', displayName: 'BUDG Status', required: false, dataType: 'LIST', aliases: ['Axon Status', 'Status', 'status', 'Object Status'], description: 'Status (dropdown from status table)' },
    { name: 'Lifecycle', displayName: 'Lifecycle', required: true, dataType: 'LIST', description: 'Lifecycle status (required, dropdown from business_area_lifecycle table)' },
    { name: 'User Email', displayName: 'User Email', required: false, dataType: 'STRING', description: 'Stakeholder user email' },
    { name: 'User First Name', displayName: 'User First Name', required: false, dataType: 'STRING', description: 'Stakeholder first name' },
    { name: 'User Last Name', displayName: 'User Last Name', required: false, dataType: 'STRING', description: 'Stakeholder last name' },
    { name: 'User Lan ID', displayName: 'User Lan ID', required: false, dataType: 'STRING', description: 'Stakeholder LAN ID' },
    { name: 'Governance Role', displayName: 'Governance Role', required: false, dataType: 'LIST', description: 'Governance role (dropdown from object_role table)' }
  ],
  UPDATE: [
    { name: 'Business Area ID', displayName: 'Business Area ID', required: true, dataType: 'INTEGER', description: 'Business Area ID (required for updates)' },
    { name: 'Business Area Name', displayName: 'Business Area Name', required: true, dataType: 'STRING', description: 'Primary name of the business area (required)' },
    { name: 'Description', displayName: 'Description', required: true, dataType: 'STRING', description: 'Detailed description (required)' },
    { name: 'BUDG Viewing', displayName: 'BUDG Viewing', required: false, dataType: 'LIST', aliases: ['Axon Viewing', 'Viewing', 'is_Public', 'Is_Public', 'Public', 'Viewing Access'], description: 'Viewing access level (dropdown from viewing table)' },
    { name: 'BUDG Status', displayName: 'BUDG Status', required: false, dataType: 'LIST', aliases: ['Axon Status', 'Status', 'status', 'Object Status'], description: 'Status (dropdown from status table)' },
    { name: 'Lifecycle', displayName: 'Lifecycle', required: true, dataType: 'LIST', description: 'Lifecycle status (required, dropdown from business_area_lifecycle table)' }
  ],
  DELETE: [
    { name: 'ID', displayName: 'ID', required: true, dataType: 'INTEGER', description: 'Business Area ID to delete' }
  ]
};

export const CAPABILITY_FIELDS = {
  INSERT: [
    { name: 'Reference', displayName: 'Reference', required: false, dataType: 'STRING', description: 'Reference number' },
    { name: 'Capability Name', displayName: 'Capability Name', required: true, dataType: 'STRING', description: 'Primary name of the capability (required)' },
    { name: 'Capability Definition', displayName: 'Capability Definition', required: true, dataType: 'STRING', description: 'Capability definition (required)' },
    { name: 'BUDG Viewing', displayName: 'BUDG Viewing', required: false, dataType: 'LIST', aliases: ['Axon Viewing', 'Viewing', 'is_Public', 'Is_Public', 'Public', 'Viewing Access'], description: 'Viewing access level (dropdown from viewing table)' },
    { name: 'Classification', displayName: 'Classification', required: true, dataType: 'LIST', description: 'Classification (required, dropdown from capability_classification table)' },
    { name: 'BUDG Status', displayName: 'BUDG Status', required: false, dataType: 'LIST', aliases: ['Axon Status', 'Status', 'status', 'Object Status'], description: 'Status (dropdown from status table)' },
    { name: 'Lifecycle', displayName: 'Lifecycle', required: true, dataType: 'LIST', description: 'Lifecycle status (required, dropdown from capability_lifecyle table)' },
    { name: 'Capability Type', displayName: 'Capability Type', required: true, dataType: 'LIST', description: 'Capability type (required, dropdown from capability_type table)' },
    { name: 'User Email', displayName: 'User Email', required: false, dataType: 'STRING', description: 'Stakeholder user email' },
    { name: 'User First Name', displayName: 'User First Name', required: false, dataType: 'STRING', description: 'Stakeholder first name' },
    { name: 'User Last Name', displayName: 'User Last Name', required: false, dataType: 'STRING', description: 'Stakeholder last name' },
    { name: 'User Lan ID', displayName: 'User Lan ID', required: false, dataType: 'STRING', description: 'Stakeholder LAN ID' },
    { name: 'Governance Role', displayName: 'Governance Role', required: false, dataType: 'LIST', description: 'Governance role (dropdown from object_role table)' }
  ],
  UPDATE: [
    { name: 'Capability ID', displayName: 'Capability ID', required: true, dataType: 'INTEGER', description: 'Capability ID (required for updates)' },
    { name: 'Reference', displayName: 'Reference', required: false, dataType: 'STRING', description: 'Reference number' },
    { name: 'Capability Name', displayName: 'Capability Name', required: true, dataType: 'STRING', description: 'Primary name of the capability (required)' },
    { name: 'Capability Definition', displayName: 'Capability Definition', required: true, dataType: 'STRING', description: 'Capability definition (required)' },
    { name: 'BUDG Viewing', displayName: 'BUDG Viewing', required: false, dataType: 'LIST', aliases: ['Axon Viewing', 'Viewing', 'is_Public', 'Is_Public', 'Public', 'Viewing Access'], description: 'Viewing access level (dropdown from viewing table)' },
    { name: 'Classification', displayName: 'Classification', required: true, dataType: 'LIST', description: 'Classification (required, dropdown from capability_classification table)' },
    { name: 'BUDG Status', displayName: 'BUDG Status', required: false, dataType: 'LIST', aliases: ['Axon Status', 'Status', 'status', 'Object Status'], description: 'Status (dropdown from status table)' },
    { name: 'Lifecycle', displayName: 'Lifecycle', required: true, dataType: 'LIST', description: 'Lifecycle status (required, dropdown from capability_lifecyle table)' },
    { name: 'Capability Type', displayName: 'Capability Type', required: true, dataType: 'LIST', description: 'Capability type (required, dropdown from capability_type table)' }
  ],
  DELETE: [
    { name: 'ID', displayName: 'ID', required: true, dataType: 'INTEGER', description: 'Capability ID to delete' }
  ]
};

export const CLIENT_FIELDS = {
  INSERT: [
    { name: 'Client Name', displayName: 'Client Name', required: true, dataType: 'STRING', description: 'Primary name of the client (required)' },
    { name: 'Long Name', displayName: 'Long Name', required: false, dataType: 'STRING', description: 'Full name of the client' },
    { name: 'Description', displayName: 'Description', required: true, dataType: 'STRING', description: 'Detailed description (required)' },
    { name: 'Parent Client Name', displayName: 'Parent Client Name', required: false, dataType: 'STRING', description: 'Name of parent client' },
    { name: 'Lifecycle', displayName: 'Lifecycle', required: true, dataType: 'LIST', description: 'Lifecycle status (required, dropdown from client_lifecycle table)' },
    { name: 'BUDG Status', displayName: 'BUDG Status', required: false, dataType: 'LIST', aliases: ['Axon Status', 'Status', 'status', 'Object Status'], description: 'Status (dropdown from status table)' },
    { name: 'BUDG Viewing', displayName: 'BUDG Viewing', required: false, dataType: 'LIST', aliases: ['Axon Viewing', 'Viewing', 'is_Public', 'Is_Public', 'Public', 'Viewing Access'], description: 'Viewing access level (dropdown from viewing table)' },
    { name: 'User Email', displayName: 'User Email', required: false, dataType: 'STRING', description: 'Stakeholder user email' },
    { name: 'User First Name', displayName: 'User First Name', required: false, dataType: 'STRING', description: 'Stakeholder first name' },
    { name: 'User Last Name', displayName: 'User Last Name', required: false, dataType: 'STRING', description: 'Stakeholder last name' },
    { name: 'User Lan ID', displayName: 'User Lan ID', required: false, dataType: 'STRING', description: 'Stakeholder LAN ID' },
    { name: 'Governance Role', displayName: 'Governance Role', required: false, dataType: 'LIST', description: 'Governance role (dropdown from object_role table)' }
  ],
  UPDATE: [
    { name: 'Client ID', displayName: 'Client ID', required: true, dataType: 'INTEGER', description: 'Client ID (required for updates)' },
    { name: 'Client Name', displayName: 'Client Name', required: true, dataType: 'STRING', description: 'Primary name of the client (required)' },
    { name: 'Long Name', displayName: 'Long Name', required: false, dataType: 'STRING', description: 'Full name of the client' },
    { name: 'Description', displayName: 'Description', required: true, dataType: 'STRING', description: 'Detailed description (required)' },
    { name: 'Parent Client Name', displayName: 'Parent Client Name', required: false, dataType: 'STRING', description: 'Name of parent client' },
    { name: 'Lifecycle', displayName: 'Lifecycle', required: true, dataType: 'LIST', description: 'Lifecycle status (required, dropdown from client_lifecycle table)' },
    { name: 'BUDG Status', displayName: 'BUDG Status', required: false, dataType: 'LIST', aliases: ['Axon Status', 'Status', 'status', 'Object Status'], description: 'Status (dropdown from status table)' },
    { name: 'BUDG Viewing', displayName: 'BUDG Viewing', required: false, dataType: 'LIST', aliases: ['Axon Viewing', 'Viewing', 'is_Public', 'Is_Public', 'Public', 'Viewing Access'], description: 'Viewing access level (dropdown from viewing table)' }
  ],
  DELETE: [
    { name: 'ID', displayName: 'ID', required: true, dataType: 'INTEGER', description: 'Client ID to delete' }
  ]
};

export const LEGAL_FIELDS = {
  INSERT: [
    { name: 'Short Name', displayName: 'Short Name', required: true, dataType: 'STRING', description: 'Short name of the legal entity (required)' },
    { name: 'Long Name', displayName: 'Long Name', required: true, dataType: 'STRING', description: 'Full name of the legal entity (required)' },
    { name: 'Description', displayName: 'Description', required: false, dataType: 'STRING', description: 'Detailed description' },
    { name: 'Parent Short Name', displayName: 'Parent Short Name', required: false, dataType: 'STRING', aliases: ['Parent Name'], description: 'Parent legal entity short name (template column: Parent Name)' },
    { name: 'BUDG Status', displayName: 'BUDG Status', required: true, dataType: 'LIST', aliases: ['Axon Status', 'Status', 'status', 'Object Status'], description: 'Status (dropdown from status table, defaults to first option)' },
    { name: 'BUDG Viewing', displayName: 'BUDG Viewing', required: true, dataType: 'LIST', aliases: ['Axon Viewing', 'Viewing', 'is_Public', 'Is_Public', 'Public', 'Viewing Access'], description: 'Viewing access level (dropdown from viewing table, defaults to first option)' },
    { name: 'User Email', displayName: 'User Email', required: false, dataType: 'STRING', description: 'Stakeholder user email' },
    { name: 'User First Name', displayName: 'User First Name', required: false, dataType: 'STRING', description: 'Stakeholder first name' },
    { name: 'User Last Name', displayName: 'User Last Name', required: false, dataType: 'STRING', description: 'Stakeholder last name' },
    { name: 'User Lan ID', displayName: 'User Lan ID', required: false, dataType: 'STRING', description: 'Stakeholder LAN ID' },
    { name: 'Governance Role', displayName: 'Governance Role', required: false, dataType: 'LIST', description: 'Governance role (dropdown from object_role table)' }
  ],
  UPDATE: [
    { name: 'Legal ID', displayName: 'Legal ID', required: true, dataType: 'INTEGER', description: 'Legal Entity ID (required for updates)' },
    { name: 'Short Name', displayName: 'Short Name', required: true, dataType: 'STRING', description: 'Short name of the legal entity (required)' },
    { name: 'Long Name', displayName: 'Long Name', required: true, dataType: 'STRING', description: 'Full name of the legal entity (required)' },
    { name: 'Description', displayName: 'Description', required: false, dataType: 'STRING', description: 'Detailed description' },
    { name: 'Parent Short Name', displayName: 'Parent Short Name', required: false, dataType: 'STRING', aliases: ['Parent Name'], description: 'Parent legal entity short name (template column: Parent Name)' },
    { name: 'BUDG Status', displayName: 'BUDG Status', required: true, dataType: 'LIST', aliases: ['Axon Status', 'Status', 'status', 'Object Status'], description: 'Status (dropdown from status table, defaults to first option)' },
    { name: 'BUDG Viewing', displayName: 'BUDG Viewing', required: true, dataType: 'LIST', aliases: ['Axon Viewing', 'Viewing', 'is_Public', 'Is_Public', 'Public', 'Viewing Access'], description: 'Viewing access level (dropdown from viewing table, defaults to first option)' }
  ],
  DELETE: [
    { name: 'ID', displayName: 'ID', required: true, dataType: 'INTEGER', description: 'Legal Entity ID to delete' }
  ]
};

export const ORG_UNIT_FIELDS = {
  INSERT: [
    { name: 'Org Unit Name', displayName: 'Org Unit Name', required: true, dataType: 'STRING', description: 'Name of the organizational unit (required)' },
    { name: 'Description', displayName: 'Description', required: false, dataType: 'STRING', description: 'Detailed description' },
    { name: 'Reference', displayName: 'Reference', required: false, dataType: 'STRING', description: 'Reference code' },
    { name: 'Parent Org Unit Reference', displayName: 'Parent Org Unit Reference', required: false, dataType: 'STRING', description: 'Parent org unit reference' },
    { name: 'BUDG Status', displayName: 'BUDG Status', required: false, dataType: 'LIST', aliases: ['Axon Status', 'Status', 'status', 'Object Status'], description: 'Status (dropdown from status table)' }
  ],
  UPDATE: [
    { name: 'Org Unit ID', displayName: 'Org Unit ID', required: false, dataType: 'INTEGER', description: 'Org Unit ID (any one of ID, Ref, Name is enough for update)' },
    { name: 'Org Unit Name', displayName: 'Org Unit Name', required: false, dataType: 'STRING', description: 'Name of the organizational unit' },
    { name: 'Description', displayName: 'Description', required: false, dataType: 'STRING', description: 'Detailed description' },
    { name: 'Reference', displayName: 'Reference', required: false, dataType: 'STRING', description: 'Reference code' },
    { name: 'Parent Org Unit Reference', displayName: 'Parent Org Unit Reference', required: false, dataType: 'STRING', description: 'Parent org unit reference' },
    { name: 'BUDG Status', displayName: 'BUDG Status', required: false, dataType: 'LIST', aliases: ['Axon Status', 'Status', 'status', 'Object Status'], description: 'Status (dropdown from status table)' }
  ],
  DELETE: [
    { name: 'ID', displayName: 'ID', required: true, dataType: 'INTEGER', description: 'Org Unit ID to delete' }
  ]
};

export const PEOPLE_FIELDS = {
  INSERT: [
    { name: 'First Name', displayName: 'First Name', required: true, dataType: 'STRING', description: 'First name (required)' },
    { name: 'Last Name', displayName: 'Last Name', required: true, dataType: 'STRING', description: 'Last name (required)' },
    { name: 'Function', displayName: 'Function', required: false, dataType: 'STRING', description: 'Function name' },
    { name: 'Function Description', displayName: 'Function Description', required: false, dataType: 'STRING', description: 'Function description' },
    { name: 'Description', displayName: 'Description', required: false, dataType: 'STRING', description: 'Description' },
    { name: 'Email', displayName: 'Email', required: true, dataType: 'STRING', description: 'Email address (required, must be unique)' },
    { name: 'Password', displayName: 'Password', required: false, dataType: 'STRING', description: 'Password' },
    { name: 'BUDG Status', displayName: 'BUDG Status', required: false, dataType: 'STRING', description: 'Status', aliases: ['Axon Status', 'Status', 'status', 'Object Status'] },
    { name: 'Org Unit Reference', displayName: 'Org Unit Reference', required: false, dataType: 'INTEGER', description: 'Organizational unit ID' },
    { name: 'Org Unit Name', displayName: 'Org Unit Name', required: false, dataType: 'STRING', description: 'Organizational unit name (alternative to ID)' },
    { name: 'Profile', displayName: 'Profile', required: true, dataType: 'STRING', description: 'Profile from role.primaryname (required)' },
    { name: 'Office Location', displayName: 'Office Location', required: false, dataType: 'STRING', description: 'Office location' },
    { name: 'Internal Mail Code', displayName: 'Internal Mail Code', required: false, dataType: 'STRING', description: 'Internal mail code' },
    { name: 'Office Telephone', displayName: 'Office Telephone', required: false, dataType: 'STRING', description: 'Office telephone' },
    { name: 'Mobile/Cell', displayName: 'Mobile/Cell', required: false, dataType: 'STRING', description: 'Mobile/cell phone' },
    { name: 'LAN Id', displayName: 'LAN Id', required: false, dataType: 'STRING', description: 'LAN ID' },
    { name: 'Employment Type', displayName: 'Employment Type', required: true, dataType: 'STRING', description: 'Employment type (required)' },
    { name: 'Lifecycle', displayName: 'Lifecycle', required: true, dataType: 'STRING', description: 'Lifecycle status (required)' }
  ],
  UPDATE: [
    { name: 'People ID', displayName: 'People ID', required: true, dataType: 'INTEGER', description: 'People ID (required for updates)' },
    { name: 'First Name', displayName: 'First Name', required: true, dataType: 'STRING', description: 'First name (required)' },
    { name: 'Last Name', displayName: 'Last Name', required: true, dataType: 'STRING', description: 'Last name (required)' },
    { name: 'Function', displayName: 'Function', required: false, dataType: 'STRING', description: 'Function name' },
    { name: 'Function Description', displayName: 'Function Description', required: false, dataType: 'STRING', description: 'Function description' },
    { name: 'Description', displayName: 'Description', required: false, dataType: 'STRING', description: 'Description' },
    { name: 'Email', displayName: 'Email', required: true, dataType: 'STRING', description: 'Email address (required)' },
    { name: 'Password', displayName: 'Password', required: false, dataType: 'STRING', description: 'Password' },
    { name: 'BUDG Status', displayName: 'BUDG Status', required: false, dataType: 'LIST', aliases: ['Axon Status', 'Status', 'status', 'Object Status'], description: 'Status (dropdown from status table)' },
    { name: 'Org Unit Reference', displayName: 'Org Unit Reference', required: false, dataType: 'INTEGER', description: 'Organizational unit ID' },
    { name: 'Org Unit Name', displayName: 'Org Unit Name', required: false, dataType: 'STRING', description: 'Organizational unit name (alternative to ID)' },
    { name: 'Profile', displayName: 'Profile', required: true, dataType: 'LIST', description: 'Profile from role.primaryname (required, dropdown from role table)' },
    { name: 'Office Location', displayName: 'Office Location', required: false, dataType: 'STRING', description: 'Office location' },
    { name: 'Internal Mail Code', displayName: 'Internal Mail Code', required: false, dataType: 'STRING', description: 'Internal mail code' },
    { name: 'Office Telephone', displayName: 'Office Telephone', required: false, dataType: 'STRING', description: 'Office telephone' },
    { name: 'Mobile/Cell', displayName: 'Mobile/Cell', required: false, dataType: 'STRING', description: 'Mobile/cell phone' },
    { name: 'LAN Id', displayName: 'LAN Id', required: false, dataType: 'STRING', description: 'LAN ID' },
    { name: 'Employment Type', displayName: 'Employment Type', required: true, dataType: 'LIST', description: 'Employment type (required, dropdown from employment_type table)' },
    { name: 'Lifecycle', displayName: 'Lifecycle', required: true, dataType: 'LIST', description: 'Lifecycle status (required, dropdown from people_lifecycle_status table)' }
  ],
  DELETE: [
    { name: 'People ID', displayName: 'People ID', required: true, dataType: 'INTEGER', description: 'People ID to delete' }
  ]
};

export const PRODUCT_FIELDS = {
  INSERT: [
    { name: 'Reference', displayName: 'Reference', required: false, dataType: 'STRING', description: 'Reference number' },
    { name: 'Product Name', displayName: 'Product Name', required: true, dataType: 'STRING', description: 'Primary name of the product (required)' },
    { name: 'Long Name', displayName: 'Long Name', required: false, dataType: 'STRING', description: 'Full name of the product' },
    { name: 'Product Description', displayName: 'Product Description', required: true, dataType: 'STRING', description: 'Product description (required)' },
    { name: 'Parent Product', displayName: 'Parent Product', required: false, dataType: 'STRING', description: 'Name of parent product' },
    { name: 'Lifecycle', displayName: 'Lifecycle', required: true, dataType: 'LIST', description: 'Lifecycle status (required, dropdown from product_lifecycle table)' },
    { name: 'BUDG Status', displayName: 'BUDG Status', required: false, dataType: 'LIST', aliases: ['Axon Status', 'Status', 'status', 'Object Status'], description: 'Status (dropdown from status table)' },
    { name: 'BUDG Viewing', displayName: 'BUDG Viewing', required: false, dataType: 'LIST', aliases: ['Axon Viewing', 'Viewing', 'is_Public', 'Is_Public', 'Public', 'Viewing Access'], description: 'Viewing access level (dropdown from viewing table)' },
    { name: 'User Email', displayName: 'User Email', required: false, dataType: 'STRING', description: 'Stakeholder user email' },
    { name: 'User First Name', displayName: 'User First Name', required: false, dataType: 'STRING', description: 'Stakeholder first name' },
    { name: 'User Last Name', displayName: 'User Last Name', required: false, dataType: 'STRING', description: 'Stakeholder last name' },
    { name: 'User Lan ID', displayName: 'User Lan ID', required: false, dataType: 'STRING', description: 'Stakeholder LAN ID' },
    { name: 'Governance Role', displayName: 'Governance Role', required: false, dataType: 'LIST', description: 'Governance role (dropdown from object_role table)' }
  ],
  UPDATE: [
    { name: 'Product ID', displayName: 'Product ID', required: false, dataType: 'INTEGER', description: 'Product ID (optional; at least one of Product ID, Reference, or Product Name is required for update)' },
    { name: 'Reference', displayName: 'Reference', required: false, dataType: 'STRING', description: 'Reference number (optional; at least one of Product ID, Reference, or Product Name is required for update)' },
    { name: 'Product Name', displayName: 'Product Name', required: false, dataType: 'STRING', description: 'Primary name (optional for update; if provided, becomes the new value)' },
    { name: 'Long Name', displayName: 'Long Name', required: false, dataType: 'STRING', description: 'Full name of the product; if provided, the value will be the new Long Name for the specified object' },
    { name: 'Product Description', displayName: 'Product Description', required: false, dataType: 'STRING', description: 'Product description (optional for update; if provided, becomes the new value)' },
    { name: 'Parent Product', displayName: 'Parent Product', required: false, dataType: 'STRING', description: 'Name of parent product' },
    { name: 'Lifecycle', displayName: 'Lifecycle', required: true, dataType: 'LIST', description: 'Lifecycle status (required, dropdown from product_lifecycle table)' },
    { name: 'BUDG Status', displayName: 'BUDG Status', required: false, dataType: 'LIST', aliases: ['Axon Status', 'Status', 'status', 'Object Status'], description: 'Status (dropdown from status table)' },
    { name: 'BUDG Viewing', displayName: 'BUDG Viewing', required: false, dataType: 'LIST', aliases: ['Axon Viewing', 'Viewing', 'is_Public', 'Is_Public', 'Public', 'Viewing Access'], description: 'Viewing access level (dropdown from viewing table)' }
  ],
  DELETE: [
    { name: 'ID', displayName: 'ID', required: true, dataType: 'INTEGER', description: 'Product ID to delete' }
  ]
};

// ============================================================================
// ROLE ENTITY FIELDS
// ============================================================================
// Common user identification fields for all role assignments
const USER_IDENTIFICATION_FIELDS = [
  { name: 'user_email', displayName: 'User Email', required: false, dataType: 'STRING', description: 'User email address (provide one of: Email, Lan ID, or First+Last Name)', aliases: ['User Email', 'Email'] },
  { name: 'user_first_name', displayName: 'User First Name', required: false, dataType: 'STRING', description: 'User first name (provide with Last Name if not using Email/Lan ID)', aliases: ['User First Name', 'First Name'] },
  { name: 'user_last_name', displayName: 'User Last Name', required: false, dataType: 'STRING', description: 'User last name (provide with First Name if not using Email/Lan ID)', aliases: ['User Last Name', 'Last Name'] },
  { name: 'user_lan_id', displayName: 'User Lan ID', required: false, dataType: 'STRING', description: 'User LAN ID (provide one of: Email, Lan ID, or First+Last Name)', aliases: ['User Lan ID', 'LAN ID', 'Lan ID'] }
];

const GOVERNANCE_ROLE_FIELD = { name: 'governance_role', displayName: 'Governance Role', required: true, dataType: 'LIST', description: 'Governance role (required, select from dropdown)', aliases: ['Governance Role', 'Role'] };

// Business Area Role
export const BUSINESS_AREA_ROLE_FIELDS = {
  INSERT: [
    { name: 'business_area_name', displayName: 'Business Area Name', required: true, dataType: 'STRING', description: 'Business Area name (required)', aliases: ['Business Area Name', 'BA Name', 'Name'] },
    { name: 'business_area_parent_name', displayName: 'Business Area Parent Name', required: false, dataType: 'STRING', description: 'Parent Business Area name (optional, for disambiguation)', aliases: ['Business Area Parent Name', 'Parent Name'] },
    ...USER_IDENTIFICATION_FIELDS,
    GOVERNANCE_ROLE_FIELD
  ],
  DELETE: [
    { name: 'business_area_name', displayName: 'Business Area Name', required: true, dataType: 'STRING', description: 'Business Area name (required)', aliases: ['Business Area Name', 'BA Name', 'Name'] },
    { name: 'business_area_parent_name', displayName: 'Business Area Parent Name', required: false, dataType: 'STRING', description: 'Parent Business Area name (optional, for disambiguation)', aliases: ['Business Area Parent Name', 'Parent Name'] },
    ...USER_IDENTIFICATION_FIELDS,
    GOVERNANCE_ROLE_FIELD
  ]
};

// Capability Role
export const CAPABILITY_ROLE_FIELDS = {
  INSERT: [
    { name: 'capability_ref', displayName: 'Capability Ref.', required: false, dataType: 'STRING', description: 'Capability reference code (provide Ref. or Name)', aliases: ['Capability Ref.', 'Ref.', 'Ref', 'Reference'] },
    { name: 'capability_name', displayName: 'Capability Name', required: false, dataType: 'STRING', description: 'Capability name (provide Ref. or Name)', aliases: ['Capability Name', 'Name'] },
    { name: 'parent_capability_name', displayName: 'Parent Capability Name', required: false, dataType: 'STRING', description: 'Parent Capability name (optional, for disambiguation)', aliases: ['Parent Capability Name', 'Parent Name'] },
    ...USER_IDENTIFICATION_FIELDS,
    GOVERNANCE_ROLE_FIELD
  ],
  DELETE: [
    { name: 'capability_ref', displayName: 'Capability Ref.', required: false, dataType: 'STRING', description: 'Capability reference code (provide Ref. or Name)', aliases: ['Capability Ref.', 'Ref.', 'Ref', 'Reference'] },
    { name: 'capability_name', displayName: 'Capability Name', required: false, dataType: 'STRING', description: 'Capability name (provide Ref. or Name)', aliases: ['Capability Name', 'Name'] },
    { name: 'parent_capability_name', displayName: 'Parent Capability Name', required: false, dataType: 'STRING', description: 'Parent Capability name (optional, for disambiguation)', aliases: ['Parent Capability Name', 'Parent Name'] },
    ...USER_IDENTIFICATION_FIELDS,
    GOVERNANCE_ROLE_FIELD
  ]
};

// Client Role
export const CLIENT_ROLE_FIELDS = {
  INSERT: [
    { name: 'client_name', displayName: 'Client Name', required: true, dataType: 'STRING', description: 'Client name (required)', aliases: ['Client Name', 'Name'] },
    { name: 'client_parent_name', displayName: 'Client Parent Name', required: false, dataType: 'STRING', description: 'Parent Client name (optional, for disambiguation)', aliases: ['Client Parent Name', 'Parent Name'] },
    ...USER_IDENTIFICATION_FIELDS,
    GOVERNANCE_ROLE_FIELD
  ],
  DELETE: [
    { name: 'client_name', displayName: 'Client Name', required: true, dataType: 'STRING', description: 'Client name (required)', aliases: ['Client Name', 'Name'] },
    { name: 'client_parent_name', displayName: 'Client Parent Name', required: false, dataType: 'STRING', description: 'Parent Client name (optional, for disambiguation)', aliases: ['Client Parent Name', 'Parent Name'] },
    ...USER_IDENTIFICATION_FIELDS,
    GOVERNANCE_ROLE_FIELD
  ]
};

// Committee Role
export const COMMITTEE_ROLE_FIELDS = {
  INSERT: [
    { name: 'committee_ref', displayName: 'Committee Ref.', required: false, dataType: 'STRING', description: 'Committee reference code (provide Ref. or Name)', aliases: ['Committee Ref.', 'Ref.', 'Ref', 'Reference'] },
    { name: 'committee_name', displayName: 'Committee Name', required: false, dataType: 'STRING', description: 'Committee name (provide Ref. or Name)', aliases: ['Committee Name', 'Name'] },
    { name: 'committee_parent_name', displayName: 'Committee Parent Name', required: false, dataType: 'STRING', description: 'Parent Committee name (optional, for disambiguation)', aliases: ['Committee Parent Name', 'Parent Name'] },
    ...USER_IDENTIFICATION_FIELDS,
    GOVERNANCE_ROLE_FIELD
  ],
  DELETE: [
    { name: 'committee_ref', displayName: 'Committee Ref.', required: false, dataType: 'STRING', description: 'Committee reference code (provide Ref. or Name)', aliases: ['Committee Ref.', 'Ref.', 'Ref', 'Reference'] },
    { name: 'committee_name', displayName: 'Committee Name', required: false, dataType: 'STRING', description: 'Committee name (provide Ref. or Name)', aliases: ['Committee Name', 'Name'] },
    { name: 'committee_parent_name', displayName: 'Committee Parent Name', required: false, dataType: 'STRING', description: 'Parent Committee name (optional, for disambiguation)', aliases: ['Committee Parent Name', 'Parent Name'] },
    ...USER_IDENTIFICATION_FIELDS,
    GOVERNANCE_ROLE_FIELD
  ]
};

// Data Quality Role
export const DATA_QUALITY_ROLE_FIELDS = {
  INSERT: [
    { name: 'ref', displayName: 'Ref.', required: false, dataType: 'STRING', description: 'Data Quality rule reference (provide Ref. or Name)', aliases: ['Ref.', 'Ref', 'Reference'] },
    { name: 'rule_name', displayName: 'Rule Name', required: false, dataType: 'STRING', description: 'Data Quality rule name (provide Ref. or Name)', aliases: ['Rule Name', 'Name'] },
    ...USER_IDENTIFICATION_FIELDS,
    GOVERNANCE_ROLE_FIELD
  ],
  DELETE: [
    { name: 'ref', displayName: 'Ref.', required: false, dataType: 'STRING', description: 'Data Quality rule reference (provide Ref. or Name)', aliases: ['Ref.', 'Ref', 'Reference'] },
    { name: 'rule_name', displayName: 'Rule Name', required: false, dataType: 'STRING', description: 'Data Quality rule name (provide Ref. or Name)', aliases: ['Rule Name', 'Name'] },
    ...USER_IDENTIFICATION_FIELDS,
    GOVERNANCE_ROLE_FIELD
  ]
};

// Data Set Role
export const DATA_SET_ROLE_FIELDS = {
  INSERT: [
    { name: 'ref', displayName: 'Ref.', required: false, dataType: 'STRING', description: 'Data Set reference (provide Ref. or Name)', aliases: ['Ref.', 'Ref', 'Reference'] },
    { name: 'name', displayName: 'Name', required: false, dataType: 'STRING', description: 'Data Set name (provide Ref. or Name)', aliases: ['Name'] },
    { name: 'system_short_name', displayName: 'System Short Name', required: false, dataType: 'STRING', description: 'System short name (optional, for disambiguation)', aliases: ['System Short Name', 'System'] },
    ...USER_IDENTIFICATION_FIELDS,
    GOVERNANCE_ROLE_FIELD
  ],
  DELETE: [
    { name: 'ref', displayName: 'Ref.', required: false, dataType: 'STRING', description: 'Data Set reference (provide Ref. or Name)', aliases: ['Ref.', 'Ref', 'Reference'] },
    { name: 'name', displayName: 'Name', required: false, dataType: 'STRING', description: 'Data Set name (provide Ref. or Name)', aliases: ['Name'] },
    { name: 'system_short_name', displayName: 'System Short Name', required: false, dataType: 'STRING', description: 'System short name (optional, for disambiguation)', aliases: ['System Short Name', 'System'] },
    ...USER_IDENTIFICATION_FIELDS,
    GOVERNANCE_ROLE_FIELD
  ]
};

// Glossary Role
export const GLOSSARY_ROLE_FIELDS = {
  INSERT: [
    { name: 'ref', displayName: 'Ref.', required: false, dataType: 'STRING', description: 'Glossary reference (provide Ref. or Name)', aliases: ['Ref.', 'Ref', 'Reference'] },
    { name: 'name', displayName: 'Name', required: false, dataType: 'STRING', description: 'Glossary term name (provide Ref. or Name)', aliases: ['Name'] },
    { name: 'parent_name', displayName: 'Parent Name', required: false, dataType: 'STRING', description: 'Parent Glossary term name (optional, for disambiguation)', aliases: ['Parent Name'] },
    ...USER_IDENTIFICATION_FIELDS,
    GOVERNANCE_ROLE_FIELD
  ],
  DELETE: [
    { name: 'ref', displayName: 'Ref.', required: false, dataType: 'STRING', description: 'Glossary reference (provide Ref. or Name)', aliases: ['Ref.', 'Ref', 'Reference'] },
    { name: 'name', displayName: 'Name', required: false, dataType: 'STRING', description: 'Glossary term name (provide Ref. or Name)', aliases: ['Name'] },
    { name: 'parent_name', displayName: 'Parent Name', required: false, dataType: 'STRING', description: 'Parent Glossary term name (optional, for disambiguation)', aliases: ['Parent Name'] },
    ...USER_IDENTIFICATION_FIELDS,
    GOVERNANCE_ROLE_FIELD
  ]
};

// Interface Role
export const INTERFACE_ROLE_FIELDS = {
  INSERT: [
    { name: 'interface_ref', displayName: 'Interface Ref.', required: false, dataType: 'STRING', description: 'Interface reference (provide Ref. or Name)', aliases: ['Interface Ref.', 'Ref.', 'Ref', 'Reference'] },
    { name: 'interface_name', displayName: 'Interface Name', required: false, dataType: 'STRING', description: 'Interface name (provide Ref. or Name)', aliases: ['Interface Name', 'Name'] },
    { name: 'interface_source_system_short_name', displayName: 'Interface Source System Short Name', required: false, dataType: 'STRING', description: 'Source system short name (optional)', aliases: ['Interface Source System Short Name', 'Source System'] },
    { name: 'interface_target_system_short_name', displayName: 'Interface Target System Short Name', required: false, dataType: 'STRING', description: 'Target system short name (optional)', aliases: ['Interface Target System Short Name', 'Target System'] },
    ...USER_IDENTIFICATION_FIELDS,
    GOVERNANCE_ROLE_FIELD
  ],
  DELETE: [
    { name: 'interface_ref', displayName: 'Interface Ref.', required: false, dataType: 'STRING', description: 'Interface reference (provide Ref. or Name)', aliases: ['Interface Ref.', 'Ref.', 'Ref', 'Reference'] },
    { name: 'interface_name', displayName: 'Interface Name', required: false, dataType: 'STRING', description: 'Interface name (provide Ref. or Name)', aliases: ['Interface Name', 'Name'] },
    { name: 'interface_source_system_short_name', displayName: 'Interface Source System Short Name', required: false, dataType: 'STRING', description: 'Source system short name (optional)', aliases: ['Interface Source System Short Name', 'Source System'] },
    { name: 'interface_target_system_short_name', displayName: 'Interface Target System Short Name', required: false, dataType: 'STRING', description: 'Target system short name (optional)', aliases: ['Interface Target System Short Name', 'Target System'] },
    ...USER_IDENTIFICATION_FIELDS,
    GOVERNANCE_ROLE_FIELD
  ]
};

// Legal Entity Role
export const LEGAL_ENTITY_ROLE_FIELDS = {
  INSERT: [
    { name: 'legal_entity_name', displayName: 'Legal Entity Name', required: true, dataType: 'STRING', description: 'Legal Entity name (required)', aliases: ['Legal Entity Name', 'Name'] },
    { name: 'legal_entity_parent_name', displayName: 'Legal Entity Parent Name', required: false, dataType: 'STRING', description: 'Parent Legal Entity name (optional, for disambiguation)', aliases: ['Legal Entity Parent Name', 'Parent Name'] },
    ...USER_IDENTIFICATION_FIELDS,
    GOVERNANCE_ROLE_FIELD
  ],
  DELETE: [
    { name: 'legal_entity_name', displayName: 'Legal Entity Name', required: true, dataType: 'STRING', description: 'Legal Entity name (required)', aliases: ['Legal Entity Name', 'Name'] },
    { name: 'legal_entity_parent_name', displayName: 'Legal Entity Parent Name', required: false, dataType: 'STRING', description: 'Parent Legal Entity name (optional, for disambiguation)', aliases: ['Legal Entity Parent Name', 'Parent Name'] },
    ...USER_IDENTIFICATION_FIELDS,
    GOVERNANCE_ROLE_FIELD
  ]
};

// Policy Role
export const POLICY_ROLE_FIELDS = {
  INSERT: [
    { name: 'ref', displayName: 'Ref.', required: false, dataType: 'STRING', description: 'Policy reference (provide Ref. or Name)', aliases: ['Ref.', 'Ref', 'Reference'] },
    { name: 'name', displayName: 'Name', required: false, dataType: 'STRING', description: 'Policy name (provide Ref. or Name)', aliases: ['Name'] },
    { name: 'parent_name', displayName: 'Parent Name', required: false, dataType: 'STRING', description: 'Parent Policy name (optional, for disambiguation)', aliases: ['Parent Name'] },
    ...USER_IDENTIFICATION_FIELDS,
    GOVERNANCE_ROLE_FIELD
  ],
  DELETE: [
    { name: 'ref', displayName: 'Ref.', required: false, dataType: 'STRING', description: 'Policy reference (provide Ref. or Name)', aliases: ['Ref.', 'Ref', 'Reference'] },
    { name: 'name', displayName: 'Name', required: false, dataType: 'STRING', description: 'Policy name (provide Ref. or Name)', aliases: ['Name'] },
    { name: 'parent_name', displayName: 'Parent Name', required: false, dataType: 'STRING', description: 'Parent Policy name (optional, for disambiguation)', aliases: ['Parent Name'] },
    ...USER_IDENTIFICATION_FIELDS,
    GOVERNANCE_ROLE_FIELD
  ]
};

// Process Role
export const PROCESS_ROLE_FIELDS = {
  INSERT: [
    { name: 'ref', displayName: 'Ref.', required: false, dataType: 'STRING', description: 'Process reference (provide Ref. or Name)', aliases: ['Ref.', 'Ref', 'Reference'] },
    { name: 'name', displayName: 'Name', required: false, dataType: 'STRING', description: 'Process name (provide Ref. or Name)', aliases: ['Name'] },
    { name: 'parent_name', displayName: 'Parent Name', required: false, dataType: 'STRING', description: 'Parent Process name (optional, for disambiguation)', aliases: ['Parent Name'] },
    ...USER_IDENTIFICATION_FIELDS,
    GOVERNANCE_ROLE_FIELD
  ],
  DELETE: [
    { name: 'ref', displayName: 'Ref.', required: false, dataType: 'STRING', description: 'Process reference (provide Ref. or Name)', aliases: ['Ref.', 'Ref', 'Reference'] },
    { name: 'name', displayName: 'Name', required: false, dataType: 'STRING', description: 'Process name (provide Ref. or Name)', aliases: ['Name'] },
    { name: 'parent_name', displayName: 'Parent Name', required: false, dataType: 'STRING', description: 'Parent Process name (optional, for disambiguation)', aliases: ['Parent Name'] },
    ...USER_IDENTIFICATION_FIELDS,
    GOVERNANCE_ROLE_FIELD
  ]
};

// Product Role
export const PRODUCT_ROLE_FIELDS = {
  INSERT: [
    { name: 'product_name', displayName: 'Product Name', required: true, dataType: 'STRING', description: 'Product name (required)', aliases: ['Product Name', 'Name'] },
    { name: 'product_parent_name', displayName: 'Product Parent Name', required: false, dataType: 'STRING', description: 'Parent Product name (optional, for disambiguation)', aliases: ['Product Parent Name', 'Parent Name'] },
    ...USER_IDENTIFICATION_FIELDS,
    GOVERNANCE_ROLE_FIELD
  ],
  DELETE: [
    { name: 'product_name', displayName: 'Product Name', required: true, dataType: 'STRING', description: 'Product name (required)', aliases: ['Product Name', 'Name'] },
    { name: 'product_parent_name', displayName: 'Product Parent Name', required: false, dataType: 'STRING', description: 'Parent Product name (optional, for disambiguation)', aliases: ['Product Parent Name', 'Parent Name'] },
    ...USER_IDENTIFICATION_FIELDS,
    GOVERNANCE_ROLE_FIELD
  ]
};

// Project Role
export const PROJECT_ROLE_FIELDS = {
  INSERT: [
    { name: 'project_ref', displayName: 'Project Ref.', required: false, dataType: 'STRING', description: 'Project reference (provide Ref. or Name)', aliases: ['Project Ref.', 'Ref.', 'Ref', 'Reference'] },
    { name: 'project_name', displayName: 'Project Name', required: false, dataType: 'STRING', description: 'Project name (provide Ref. or Name)', aliases: ['Project Name', 'Name'] },
    { name: 'project_parent_name', displayName: 'Project Parent Name', required: false, dataType: 'STRING', description: 'Parent Project name (optional, for disambiguation)', aliases: ['Project Parent Name', 'Parent Name'] },
    ...USER_IDENTIFICATION_FIELDS,
    GOVERNANCE_ROLE_FIELD
  ],
  DELETE: [
    { name: 'project_ref', displayName: 'Project Ref.', required: false, dataType: 'STRING', description: 'Project reference (provide Ref. or Name)', aliases: ['Project Ref.', 'Ref.', 'Ref', 'Reference'] },
    { name: 'project_name', displayName: 'Project Name', required: false, dataType: 'STRING', description: 'Project name (provide Ref. or Name)', aliases: ['Project Name', 'Name'] },
    { name: 'project_parent_name', displayName: 'Project Parent Name', required: false, dataType: 'STRING', description: 'Parent Project name (optional, for disambiguation)', aliases: ['Project Parent Name', 'Parent Name'] },
    ...USER_IDENTIFICATION_FIELDS,
    GOVERNANCE_ROLE_FIELD
  ]
};

// Regulation Role
export const REGULATION_ROLE_FIELDS = {
  INSERT: [
    { name: 'regulation_ref', displayName: 'Regulation Ref.', required: false, dataType: 'STRING', description: 'Regulation reference (provide Ref. or Name)', aliases: ['Regulation Ref.', 'Ref.', 'Ref', 'Reference'] },
    { name: 'regulation_name', displayName: 'Regulation Name', required: false, dataType: 'STRING', description: 'Regulation name (provide Ref. or Name)', aliases: ['Regulation Name', 'Name'] },
    { name: 'parent_regulation_name', displayName: 'Parent Regulation Name', required: false, dataType: 'STRING', description: 'Parent Regulation name (optional, for disambiguation)', aliases: ['Parent Regulation Name', 'Parent Name'] },
    ...USER_IDENTIFICATION_FIELDS,
    GOVERNANCE_ROLE_FIELD
  ],
  DELETE: [
    { name: 'regulation_ref', displayName: 'Regulation Ref.', required: false, dataType: 'STRING', description: 'Regulation reference (provide Ref. or Name)', aliases: ['Regulation Ref.', 'Ref.', 'Ref', 'Reference'] },
    { name: 'regulation_name', displayName: 'Regulation Name', required: false, dataType: 'STRING', description: 'Regulation name (provide Ref. or Name)', aliases: ['Regulation Name', 'Name'] },
    { name: 'parent_regulation_name', displayName: 'Parent Regulation Name', required: false, dataType: 'STRING', description: 'Parent Regulation name (optional, for disambiguation)', aliases: ['Parent Regulation Name', 'Parent Name'] },
    ...USER_IDENTIFICATION_FIELDS,
    GOVERNANCE_ROLE_FIELD
  ]
};

// System Role
export const SYSTEM_ROLE_FIELDS = {
  INSERT: [
    { name: 'short_name', displayName: 'Short Name', required: true, dataType: 'STRING', description: 'System short name (required)', aliases: ['Short Name', 'Name'] },
    ...USER_IDENTIFICATION_FIELDS,
    GOVERNANCE_ROLE_FIELD
  ],
  DELETE: [
    { name: 'short_name', displayName: 'Short Name', required: true, dataType: 'STRING', description: 'System short name (required)', aliases: ['Short Name', 'Name'] },
    ...USER_IDENTIFICATION_FIELDS,
    GOVERNANCE_ROLE_FIELD
  ]
};