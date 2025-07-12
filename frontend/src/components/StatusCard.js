export default function StatusCard({ status, onStartStop }) {
  const daemonStatus = status.daemon_status;
  const isRunning = daemonStatus === 'RUNNING';
  
  return (
    <div className="card">
      <div className="card-header">
        <h5>Monitor Status</h5>
      </div>
      
      <div className="card-body">
        {/* Daemon Status */}
        <div className="d-flex justify-content-between align-items-center mb-3">
          <div>
            <strong>Daemon: </strong> 
            <span className={`badge ${isRunning ? 'badge-success' : 'badge-danger'}`} style={{ marginLeft: '8px' }}>
              {daemonStatus}
            </span>
          </div>
          <button
            onClick={() => onStartStop(isRunning ? 'stop' : 'start')}
            className={`btn btn-sm ${isRunning ? 'btn-danger' : 'btn-success'}`}
            disabled={false}
          >
            {isRunning ? 'Stop' : 'Start'}
          </button>
        </div>
        
        
        {/* Next Action */}
        <div className="mb-3">
          <strong>Next Action: </strong>
          <div className="text-gray-600" style={{ fontSize: '0.875rem' }}>
            {status.next_action || 'none'}
          </div>
        </div>
        
        {/* Last Check */}
        <div className="mb-3">
          <strong>Last Check: </strong>
          <div className="text-gray-600" style={{ fontSize: '0.875rem' }}>
            {status.last_check ? new Date(status.last_check).toLocaleString() : 'Never'}
          </div>
        </div>
        
        {/* Timing Info */}
        {status.primary_down_since && (
          <div className="alert alert-danger">
            <strong>Primary Down Since: </strong>
            <div style={{ fontSize: '0.875rem' }}>
              {new Date(status.primary_down_since).toLocaleString()}
            </div>
          </div>
        )}
        
        {status.secondary_down_since && (
          <div className="alert alert-danger">
            <strong>Secondary Down Since: </strong>
            <div style={{ fontSize: '0.875rem' }}>
              {new Date(status.secondary_down_since).toLocaleString()}
            </div>
          </div>
        )}
        
        {status.primary_up_since && status.current_active === 'secondary' && (
          <div className="alert alert-info">
            <strong>Primary Up Since: </strong>
            <div style={{ fontSize: '0.875rem' }}>
              {new Date(status.primary_up_since).toLocaleString()}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}