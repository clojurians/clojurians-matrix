## clojurians-matrix

These repo is exploring the potential for a discovery site,
application and [Matrix](https://matrix.org) directory server for
Clojurians Slack community.

#### Goals

- provide infrastructure to maintain a list of rooms that are vetted by a set of administrators
- provide a web ui to browse & "discover" these rooms rooms
- provide a Matrix [directory server endpoint](https://matrix.org/docs/api/client-server/#!/Room_discovery/get_matrix_client_r0_publicRooms) that can be used by clients to provide different sets of rooms to users

Currently we're faking being a real Matrix home server and just implement the `publicRooms` directory server endpoint by serving a static file at the right location.

#### Adding a Room to the Directory

1. Invite an admin
2. Make sure the room matches all criteria
3. Submit a pull request which adds a map with at least the following fields to `resources/rooms.edn`

        {:matrix/internal-id "!gWEkPbprAwBHgNtVcH:matrix.org"
         :matrix/room-type :matrix/native}


#### How to run

```
boot fetch-rooms dev                  ; development mode
boot production fetch-rooms watch run ; production mode
```

#### Deploying

For deployment you currently need a file `clojurians-martinklepsch-com.confetti.edn` that contains AWS credentials and the name of an S3 bucket to push to. (See `deploy` for details.)

```
boot production fetch-rooms deploy
```

**Note:** a deployment of the site can be found at https://d3981087m4idf6.cloudfront.net
