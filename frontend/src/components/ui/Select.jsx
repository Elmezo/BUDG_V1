import React from 'react';
import { useTranslation } from '../../hooks/useTranslation';

const Select = ({ label, value, onChange, options, required = false, disabled = false, placeholder = 'Select...', grouped = false }) => {
  const { t } = useTranslation();
  const displayPlaceholder = (placeholder === 'Select...' || placeholder === undefined) ? t('placeholder.select', 'Select...') : placeholder;
  return (
    <div className="mb-4">
      {label && (
        <label className="block text-sm font-medium text-gray-700 mb-1">
          {label}
          {required && <span className="text-danger ml-1">*</span>}
        </label>
      )}
      <select
        value={value}
        onChange={(e) => onChange(e.target.value)}
        disabled={disabled}
        required={required}
        className="w-full px-3 py-2 border border-gray-300 rounded focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent disabled:bg-gray-100 disabled:cursor-not-allowed"
      >
        <option value="">{displayPlaceholder}</option>
        {grouped ? (
          Object.entries(options).map(([groupName, groupOptions]) => (
            <optgroup key={groupName} label={groupName}>
              {groupOptions.map((option) => (
                <option key={option.value || option} value={option.value || option}>
                  {option.label || option}
                </option>
              ))}
            </optgroup>
          ))
        ) : (
          options.map((option) => (
            <option key={option.value || option} value={option.value || option}>
              {option.label || option}
            </option>
          ))
        )}
      </select>
    </div>
  );
};

export default Select;

