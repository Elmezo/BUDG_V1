import React from 'react';

const Progress = ({ value, max = 100, className = '' }) => {
  const percentage = Math.min(100, Math.max(0, (value / max) * 100));

  return (
    <div className={`w-full bg-gray-200 rounded-full h-4 ${className}`}>
      <div
        className="bg-primary h-4 rounded-full transition-all duration-300 ease-out"
        style={{ width: `${percentage}%` }}
      >
        <span className="flex items-center justify-center h-full text-xs text-white font-medium">
          {percentage > 10 && `${Math.round(percentage)}%`}
        </span>
      </div>
    </div>
  );
};

export default Progress;

