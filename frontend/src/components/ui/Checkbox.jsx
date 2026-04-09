import React from 'react';

const Checkbox = ({ label, checked, onChange, disabled = false }) => {
  return (
    <div className="flex items-center mb-4">
      <input
        type="checkbox"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
        disabled={disabled}
        className="w-4 h-4 text-primary border-gray-300 rounded focus:ring-primary focus:ring-2 disabled:cursor-not-allowed"
      />
      {label && (
        <label className="ml-2 text-sm text-gray-700 cursor-pointer" onClick={() => !disabled && onChange(!checked)}>
          {label}
        </label>
      )}
    </div>
  );
};

export default Checkbox;

