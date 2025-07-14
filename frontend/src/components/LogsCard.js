import React, { useState } from 'react';

export default function LogsCard({ status }) {
  const [isExpanded, setIsExpanded] = useState(false);
  
  return (
    <div className="card">
      <div className="card-header">
        <div className="flex justify-between items-center">
          <div>
            <h3 className="card-title">System Information</h3>
            <p className="card-subtitle">Configuration and status details</p>
          </div>
          <button
            onClick={() => setIsExpanded(!isExpanded)}
            className="btn btn-secondary btn-sm"
          >
            {isExpanded ? 'Hide Details' : 'Show Details'}
          </button>
        </div>
      </div>
      
      <div className="card-body">
        {!isExpanded ? (
          <div className="flex flex-col gap-3">
            <div className="flex justify-between items-center">
              <span>Current Active:</span>
              <span className="badge badge-primary">
                {status.current_active === 'PRIMARY' ? 'PRIMARY' : 
                 status.current_active === 'SECONDARY' ? 'SECONDARY' : 
                 'NONE'}
              </span>
            </div>
            
            <div className="flex justify-between items-center">
              <span>Primary Status:</span>
              <span className={`badge ${status.primary_status === 'up' ? 'badge-success' : 'badge-danger'}`}>
                {status.primary_status === 'up' ? 'UP' : 'DOWN'}
              </span>
            </div>
            
            <div className="flex justify-between items-center">
              <span>Secondary Status:</span>
              <span className={`badge ${status.secondary_status === 'up' ? 'badge-success' : 'badge-danger'}`}>
                {status.secondary_status === 'up' ? 'UP' : 'DOWN'}
              </span>
            </div>
          </div>
        ) : (
          <div>
            <div className="mb-4">
              <h4 className="font-semibold mb-3">Configuration</h4>
              <div style={{ 
                background: 'var(--primary-bg)', 
                padding: 'var(--spacing-md)', 
                borderRadius: 'var(--radius-lg)', 
                fontSize: '0.875rem' 
              }}>
                <div className="grid grid-cols-1 gap-3">
                  <div>
                    <strong>Primary:</strong> {status.config?.primary?.name}<br />
                    <span style={{ color: 'var(--text-secondary)' }}>
                      {status.config?.primary?.host}:{status.config?.primary?.port}
                    </span>
                  </div>
                  <div>
                    <strong>Secondary:</strong> {status.config?.secondary?.name}<br />
                    <span style={{ color: 'var(--text-secondary)' }}>
                      {status.config?.secondary?.host}:{status.config?.secondary?.port}
                    </span>
                  </div>
                </div>
              </div>
            </div>
            
            <div>
              <h4 className="font-semibold mb-3">Full Status JSON</h4>
              <pre style={{ 
                background: 'var(--text-primary)', 
                color: '#e2e8f0',
                padding: 'var(--spacing-md)', 
                borderRadius: 'var(--radius-lg)', 
                fontSize: '0.75rem', 
                overflowX: 'auto',
                maxHeight: '200px',
                fontFamily: 'var(--font-mono)'
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