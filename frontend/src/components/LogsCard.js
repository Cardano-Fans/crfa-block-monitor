import React, { useState } from 'react';

export default function LogsCard({ status }) {
  const [isExpanded, setIsExpanded] = useState(false);
  
  return (
    <div className="card">
      <div className="card-header">
        <div className="d-flex justify-content-between align-items-center">
          <h5>System Information</h5>
          <div style={{ marginLeft: 'auto' }}>
            <button
              onClick={() => setIsExpanded(!isExpanded)}
              className="btn btn-secondary btn-sm"
            >
              {isExpanded ? 'Hide Details' : 'Show Details'}
            </button>
          </div>
        </div>
      </div>
      
      <div className="card-body">
        {!isExpanded ? (
          <div style={{ lineHeight: '1.8' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '1rem', alignItems: 'center' }}>
              <span>Current Active: </span>
              <strong>{status.current_active}</strong>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '1rem', alignItems: 'center' }}>
              <span>Primary Status: </span>
              <strong style={{ color: status.primary_status === 'up' ? '#2e7d32' : '#d32f2f' }}>{status.primary_status?.toUpperCase()}</strong>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '1rem', alignItems: 'center' }}>
              <span>Secondary Status: </span>
              <strong style={{ color: status.secondary_status === 'up' ? '#2e7d32' : '#d32f2f' }}>{status.secondary_status?.toUpperCase()}</strong>
            </div>
          </div>
        ) : (
          <div>
            <div className="mb-3">
              <h6>Configuration</h6>
              <div style={{ backgroundColor: '#f8f9fa', padding: '1rem', borderRadius: '0.375rem', fontSize: '0.875rem' }}>
                <div className="row">
                  <div className="col-6">
                    <strong>Primary: </strong> {status.config?.primary?.name}<br />
                    <span className="text-gray-600">{status.config?.primary?.host}:{status.config?.primary?.port}</span>
                  </div>
                  <div className="col-6">
                    <strong>Secondary: </strong> {status.config?.secondary?.name}<br />
                    <span className="text-gray-600">{status.config?.secondary?.host}:{status.config?.secondary?.port}</span>
                  </div>
                </div>
              </div>
            </div>
            
            <div>
              <h6>Full Status JSON</h6>
              <pre style={{ 
                backgroundColor: '#f1f3f4', 
                padding: '1rem', 
                borderRadius: '0.375rem', 
                fontSize: '0.75rem', 
                overflowX: 'auto',
                maxHeight: '200px'
              }}>
                {JSON.stringify(status, null, 2)}
              </pre>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}