var FeedBox = React.createClass({
  loadFeeds: function() {
    $.ajax({
      url: this.props.url,
      dataType: 'json',
      cache: false,
      success: function(data) {
        this.setState({data: data});
      }.bind(this),
      error: function(xhr, status, err) {
        window.location.replace("login?error=Log in first");
      }.bind(this)
    });
  },
  getInitialState: function() {
    return {data: []};
  },
  componentDidMount: function() {
    this.loadFeeds();
  },
  deliverButtonClick: function() {
    ReactDOM.render(
      <DeliverModal />,
      document.getElementById('modal')
    );
    $('#delivery_modal').openModal()
  },
  render: function() {
    return (
      <div className="container" id="subs-container">
        <div className="row">
          <div className="col s12 m6">
            <a onClick={this.deliverButtonClick} className="waves-effect waves-light btn modal-trigger" id="delivery_modal_btn" href="#delivery_modal">Deliver now</a>
          </div>
          <div className="input-field col offset-m3 s12 m3" id="search">
            <input id="search_box" type="search" required />
            <label htmlFor="search_box"><i className="material-icons">search</i></label>
          </div>
        </div>
        <FeedList data={this.state.data} />
      </div>
    );
  }
});

var FeedList = React.createClass({
  render: function() {
  var feedNodes = this.props.data.map(function(feed) {
    return (
      <Feed title={feed.title} key={feed.feedId} lastDelivery={feed.lastDelivery}>

      </Feed>
    );
    });
    return (
    <form>
      <table className="highlight" id="subscriptions">
        <thead>
        <tr>
          <th></th>
          <th>Title</th>
          <th>Last delivery</th>
        </tr>
        </thead>

        <tbody>
          {feedNodes}
        </tbody>
      </table>
      </form>
    );
  }
});

var Feed = React.createClass({
  render: function() {
    return (
      <tr>
        <td><input type="checkbox" className="filled-in" id={this.props.key} name={this.props.key} /><label for={this.props.key}></label></td>
        <td className="feed_title">{this.props.title}</td>
        <td>{this.props.lastDelivery != null ? moment(this.props.lastDelivery.deliveryDate).fromNow() : ''}</td>
      </tr>
    );
  }
});

var DeliverModal = React.createClass({
  getInitialState: function() {
    return {mode: 'simple'};
  },
  modeChangeClick: function(event) {
    this.setState({
      'mode': event.target.checked ? 'detailed' : 'simple'
    });
  },
  render: function() {
    var feeds = [{'title':'lala','feedId':'id'}]
    var mode = this.state.mode;
    var inProgress = 'false';
    var progressbar = inProgress == 'true' ?
      <div className="progress" id="delivery_progress">
        <div className="indeterminate"></div>
      </div> : ''

    var list
    if (mode == 'simple'){
      var actualList = feeds.map(function(feed) {
          return (
            <li className='collection-item' key={feed.feedId}>
            <div feed_id={feed.feedId} title={feed.title}>
            {feed.title}
            </div></li>
          );
        });
      list =
      <div id="simple">
       <p>
         <input type="checkbox" className="filled-in" id="include_images"/>
         <label htmlFor="include_images">Include images</label>
       </p>
       <p>
         <input type="checkbox" className="filled-in" id="mark_as_read" defaultChecked/>
         <label htmlFor="mark_as_read">Mark as read</label>
       </p>
       <p>
         <input type="checkbox" className="filled-in" id="full_article" defaultChecked/>
         <label htmlFor="full">Full article</label>
       </p>
       <ul className="collection" id="feed_list">
          {actualList}
       </ul>
      </div>
     } else {
       var actualList = feeds.map(function(feed) {
           return (
              <li className='collection-item' key={feed.feedId}>
               <div feed_id={feed.feedId} title={feed.title} >
                   {feed.title}
                 <p>
                   <input type="checkbox" className="filled-in" id="include_images"/>
                   <label htmlFor="include_images">Include images</label>
                 </p>
                 <p>
                   <input type="checkbox" className="filled-in" id="mark_as_read" defaultChecked/>
                   <label htmlFor="mark_as_read">Mark as read</label>
                 </p>
                 <p>
                   <input type="checkbox" className="filled-in" id="full_article" defaultChecked/>
                   <label htmlFor="full">Full article</label>
                 </p>
               </div></li>
           );
         });
       list =
       <div id="detailed">
           <ul className="collection" id="detailed_feed_list">
            {actualList}
           </ul>
       </div>
     }
    return (
      <div id="delivery_modal" className="modal">
          <div className="modal-content" id="delivery_form">
              <h4>Deliver feeds</h4>
              {progressbar}
              <ModeSwitch onChange={this.modeChangeClick} mode={mode} />
              {list}
          </div>
          <div className="modal-footer">
              <a href="#!" className="modal-action modal-close waves-effect waves-red btn-flat">Cancel</a>
              <a href="#!" className="modal-action waves-effect waves-green btn-flat submit save" id="delivery_save_btn">Deliver</a>
          </div>
      </div>
    );
  }
});

var ModeSwitch = React.createClass({
  render: function(){
    var mode = this.props.mode;
    var checkbox = mode == 'simple' ?
      <input onChange={this.props.onChange} type="checkbox" id="delivery_mode"/> :
      <input onChange={this.props.onChange} type="checkbox" id="delivery_mode" checked="checked" />
    return (
      <div className="switch right-align">
         <label>
             Simple
             {checkbox}
             <span className="lever"></span>
             Detailed
         </label>
      </div>
    )
  }
});

ReactDOM.render(
  <FeedBox url="api/feeds" />,
  document.getElementById('content')
);

$(document).ready(function(){
  $("#search_box").keyup(function() {
    var columns, filter, i, j, ref, results, subscription, subscriptions, subscriptionsLength, text;
    filter = $(this).val();
    subscriptions = $('#subscriptions').find('tr');
    subscriptionsLength = subscriptions.length;
    results = [];
    for (i = j = 0, ref = subscriptionsLength; 0 <= ref ? j < ref : j > ref; i = 0 <= ref ? ++j : --j) {
      subscription = subscriptions.eq(i);
      columns = subscription.find('td');
      if (columns.length > 0) {
        text = columns.eq(1).text();
        if (text.search(new RegExp(filter, "i")) < 0) {
          results.push(subscription.hide());
        } else {
          results.push(subscription.show());
        }
      } else {
        results.push(void 0);
      }
    }
    return results;
  });
});


