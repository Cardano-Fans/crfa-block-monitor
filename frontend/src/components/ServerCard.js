export default function ServerCard({ server, isActive, onSwitch, systemStatus }) {
  const isSystemDown = systemStatus === 'NONE';
  const cardClass = `card ${isActive ? 'card-active' : ''} ${isSystemDown && server.status === 'down' ? 'card-critical' : ''}`;
  
  return (
    <div className={cardClass}>
      <div className="card-header">
        <div className="flex justify-between items-center">
          <div>
            <h3 className="card-title">
              {server.type.charAt(0).toUpperCase() + server.type.slice(1)} Server
            </h3>
            <p className="card-subtitle">{server.name} ({server.host}:{server.port})</p>
          </div>
          {isActive && (
            <span className="badge badge-success">
              ACTIVE
            </span>
          )}
        </div>
      </div>
      
      <div className="card-body">
        <div className="text-center mb-4">
          <div className={`status-indicator ${
            server.status === 'up' ? 'status-up' : 
            server.status === 'down' ? 'status-down' : 
            'status-unknown'
          }`}>
            {server.status === 'up' && 'Online'}
            {server.status === 'down' && 'Offline'}
            {server.status === 'unknown' && 'Unknown'}
          </div>
        </div>
        
        <div className="mt-auto">
          {!isActive && (
            <button
              onClick={() => onSwitch(server.type)}
              className={`btn w-full ${server.type === 'primary' ? 'btn-primary' : 'btn-warning'}`}
              disabled={server.status !== 'up'}
            >
              Switch to {server.type.charAt(0).toUpperCase() + server.type.slice(1)}
            </button>
          )}
          
          {isActive && (
            <div className="text-center">
              <div className="alert alert-success">
                <strong>Currently Active</strong><br/>
                This server is handling traffic
              </div>
            </div>
          )}
          
          {isSystemDown && server.status === 'down' && (
            <div className="text-center">
              <div className="alert alert-danger">
                <strong>System Critical</strong><br/>
                No active server available
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}