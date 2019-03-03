// Copyright 2019, Oracle Corporation and/or its affiliates. All rights reserved.

import React, { useState, useEffect } from 'react'
import axios from 'axios'
import { Card, CardBody, CardTitle, CardText } from 'reactstrap'
import { REST_ADDRESS, REST_AUTH_HEADER } from '../shared/Rest'

export const About = (props) => {

    //
    //  Retrieve details of the operator
    //
    const [operator, setOperator] = useState([])
    useEffect(() => {
        axios(REST_ADDRESS + 'operator', {
            method: 'get',
            headers: {
                'Authorization': REST_AUTH_HEADER,
                'Accept': 'application/json'
            },
            withCredentials: false
        })
            .then(response => {
                console.log(response)
                if (response.status === 200) {
                    return response
                } else {
                    var error = new Error('Error ' + response.status + ': ' + response.statusText)
                    error.response = response
                    throw error
                }
            }, error => {
                var errmess = new Error(error.message)
                throw errmess
            })
            .then(response => response.data.items[0])
            .then(operator => setOperator(operator))
            .catch(error => console.log(error.message))
    }, [])


    return (
        <div className="container">
            <p>&nbsp;</p>
            <div className="row align-items-start">
                <div className="col-12 col-md m-1">
                    <p>Something else will go here!</p>
                </div>
                <div className="col-12 col-md-5 m-1">
                    <Card>
                        <CardBody>
                            <CardTitle>About this operator</CardTitle>
                            <CardText>REST API version: {operator.version}</CardText>
                        </CardBody>
                    </Card>
                </div>
            </div>
        </div>
    )
}
