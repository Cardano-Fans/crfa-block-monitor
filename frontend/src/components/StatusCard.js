export default function StatusCard({ status, onStartStop }) {
  const daemonStatus = status.daemon_status;
  const isRunning = daemonStatus === 'RUNNING';
  
  return (
    <div className="card">
      <div className="card-header">
        <h3 className="card-title">Monitor Status</h3>
        <p className="card-subtitle">System monitoring and control</p>
      </div>
      
      <div className="card-body">
        {/* Daemon Status */}
        <div className="flex justify-between items-center mb-3">
          <div>
            <strong>Daemon Status:</strong> 
            <span className={`badge ${isRunning ? 'badge-success' : 'badge-danger'}`} style={{ marginLeft: '8px' }}>
              {isRunning ? 'RUNNING' : 'STOPPED'}
            </span>
          </div>
          <button
            onClick={() => onStartStop(isRunning ? 'stop' : 'start')}
            className={`btn btn-sm ${isRunning ? 'btn-danger' : 'btn-success'}`}
          >
            {isRunning ? 'Stop' : 'Start'}
          </button>
        </div>
        
        {/* Next Action */}
        <div className="mb-3">
          <strong>Next Action:</strong>
          <div className="text-sm mt-1">
            <span className="badge badge-primary">
              {status.next_action || 'NONE'}
            </span>
          </div>
        </div>
        
        {/* Last Check */}
        <div className="mb-3">
          <strong>Last Check:</strong>
          <div className="text-sm mt-1" style={{ color: 'var(--text-secondary)' }}>
            {status.last_check ? new Date(status.last_check).toLocaleString() : 'Never'}
          </div>
        </div>
        
        {/* Timing Info */}
        {status.primary_down_since && (
          <div className="alert alert-danger">
            <strong>Primary Down Since:</strong>
            <div className="text-sm mt-1">
              {new Date(status.primary_down_since).toLocaleString()}
            </div>
          </div>
        )}
        
        {status.secondary_down_since && (
          <div className="alert alert-danger">
            <strong>Secondary Down Since:</strong>
            <div className="text-sm mt-1">
              {new Date(status.secondary_down_since).toLocaleString()}
            </div>
          </div>
        )}
        
        {status.primary_up_since && status.current_active === 'SECONDARY' && (
          <div className="alert alert-info">
            <strong>Primary Up Since:</strong>
            <div className="text-sm mt-1">
              {new Date(status.primary_up_since).toLocaleString()}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}