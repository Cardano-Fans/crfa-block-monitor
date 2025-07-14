export default function ServerCard({ server, isActive, onSwitch }) {
  const cardClass = `card ${isActive ? 'active' : ''}`;
  
  return (
    <div className={cardClass}>
      <div className="card-header">
        <h5>{server.type.charAt(0).toUpperCase() + server.type.slice(1)} Server</h5>
        <small className="text-gray-600">{server.name} ({server.host}:{server.port})</small>
        {isActive && (
          <div style={{ float: 'right' }}>
            <span className="badge badge-success">ACTIVE</span>
          </div>
        )}
      </div>
      
      <div className="card-body">
        <div style={{ textAlign: 'center', marginBottom: '2rem' }}>
          {server.status === 'up' && (
            <div className="status-online" style={{ color: '#2e7d32', fontWeight: 'bold' }}>● UP</div>
          )}
          {server.status === 'down' && (
            <div className="status-offline" style={{ color: '#d32f2f', fontWeight: 'bold' }}>● DOWN</div>
          )}
          {server.status === 'unknown' && (
            <div className="status-unknown" style={{ color: '#ed6c02', fontWeight: 'bold' }}>● UNKNOWN</div>
          )}
        </div>
        
        <div style={{ marginTop: 'auto' }}>
          {/* Conditional switch button for when server is up */}
          {server.status === 'up' && !isActive && (
            <button
              onClick={() => onSwitch(server.type)}
              className={`btn ${server.type === 'primary' ? 'btn-primary' : 'btn-secondary'}`}
              style={{ width: '100%', marginBottom: '10px' }}
            >
              Switch to {server.type.charAt(0).toUpperCase() + server.type.slice(1)}
            </button>
          )}

          {/* Always visible colorful switch button */}
          <button
            onClick={() => onSwitch(server.type)}
            className={`btn ${server.type === 'primary' ? 'btn-primary' : 'btn-warning'}`}
            style={{ 
              width: '100%', 
              fontSize: '14px', 
              fontWeight: 'bold',
              background: server.type === 'primary' ? 'linear-gradient(45deg, #2196F3, #1976D2)' : 'linear-gradient(45deg, #FF9800, #F57C00)',
              border: 'none',
              boxShadow: '0 3px 6px rgba(0,0,0,0.16)',
              textTransform: 'uppercase',
              letterSpacing: '1px'
            }}
            title={`Switch to ${server.type} server`}
          >
            Switch To {server.type.charAt(0).toUpperCase() + server.type.slice(1)}
          </button>
        </div>
      </div>
    </div>
  );
}