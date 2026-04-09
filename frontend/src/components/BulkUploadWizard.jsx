import React, { useState } from 'react';
import StepChooseFile from './StepChooseFile';
import StepMapColumns from './StepMapColumns';
import StepUploadProgress from './StepUploadProgress';
import { useTranslation } from '../hooks/useTranslation';

const BulkUploadWizard = () => {
  const { t } = useTranslation();
  const [currentStep, setCurrentStep] = useState(1);
  const [stepData, setStepData] = useState({});

  // Handle navigation to Step 2
  const handleStep1Next = (data) => {
    setStepData(data);
    setCurrentStep(2);
  };

  // Handle navigation to Step 3
  const handleStep2Next = (data) => {
    setStepData(data);
    setCurrentStep(3);
  };

  // Handle back to Step 1
  const handleBackToStep1 = () => {
    setCurrentStep(1);
  };

  // Handle start over (reset everything)
  const handleStartOver = () => {
    setStepData({});
    setCurrentStep(1);
  };

  // Render step indicator
  const renderStepIndicator = () => {
    const steps = [
      { number: 1, title: t('bulkUpload.steps.chooseFile', 'Choose File') },
      { number: 2, title: t('bulkUpload.steps.mapColumns', 'Map Columns') },
      { number: 3, title: t('bulkUpload.steps.upload', 'Upload') }
    ];

    return (
      <div className="mb-8">
        <div className="flex items-center justify-center">
          {steps.map((step, index) => (
            <React.Fragment key={step.number}>
              {/* Step Circle */}
              <div className="flex flex-col items-center">
                <div
                  className={`flex items-center justify-center w-12 h-12 rounded-full font-bold text-lg transition-colors ${
                    currentStep === step.number
                      ? 'bg-green-600 text-white shadow-lg'
                      : currentStep > step.number
                      ? 'bg-green-500 text-white'
                      : 'bg-gray-200 text-gray-500'
                  }`}
                >
                  {currentStep > step.number ? (
                    <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                    </svg>
                  ) : (
                    step.number
                  )}
                </div>
                <div
                  className={`mt-2 text-base font-semibold ${
                    currentStep === step.number ? 'text-gray-900' : 'text-gray-500'
                  }`}
                >
                  {step.title}
                </div>
              </div>

              {/* Connector Line */}
              {index < steps.length - 1 && (
                <div
                  className={`w-32 h-1.5 mx-3 transition-colors rounded-full ${
                    currentStep > step.number ? 'bg-green-500' : 'bg-gray-200'
                  }`}
                />
              )}
            </React.Fragment>
          ))}
        </div>
      </div>
    );
  };

  return (
    <div className="min-h-full bg-white py-8 px-4">
      <div className="max-w-4xl mx-auto">
        {/* Header */}
        <div className="text-center mb-8">
          <h1 className="text-4xl font-bold text-gray-900 mb-3">{t('bulkUpload.title', 'Bulk Upload Wizard')}</h1>
          <p className="text-lg text-gray-700">{t('bulkUpload.subtitle', 'Upload and process your data in 3 easy steps')}</p>
        </div>

        {/* Step Indicator */}
        {renderStepIndicator()}

        {/* Step Content */}
        <div className="mt-8">
          {currentStep === 1 && (
            <StepChooseFile 
              onNext={handleStep1Next} 
              initialData={stepData}
            />
          )}

          {currentStep === 2 && (
            <StepMapColumns 
              stepData={stepData}
              onNext={handleStep2Next}
              onBack={handleBackToStep1}
            />
          )}

          {currentStep === 3 && (
            <StepUploadProgress 
              stepData={stepData}
              onStartOver={handleStartOver}
            />
          )}
        </div>

        {/* Footer */}
        <div className="mt-12 text-center text-base text-gray-600">
          <p>{t('bulkUpload.footer.needHelp', 'Need help? Check the documentation or contact support.')}</p>
        </div>
      </div>
    </div>
  );
};

export default BulkUploadWizard;

